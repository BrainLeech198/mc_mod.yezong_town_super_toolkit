package com.yztc.auth.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 用铁砧改名框接收一段文本。
 *
 * <p>原版铁砧在输入槽为空时不会发送改名包（{@code AnvilScreen.onNameChanged} 见到空槽即返回），
 * 且名字框仅在槽内有物品时才可编辑，故槽内放一张占位纸（spec §4.5）。
 *
 * <p>改名包是**逐键**发送的，故不能用 {@code setItemName} 作为提交点（会只捕获首字符）；
 * 真正的提交点是关屏（{@code removed}，玩家按 Esc 关闭），此时 {@code last} 持有完整输入。
 */
public final class AnvilInput {
    private AnvilInput() {}

    public static void open(ServerPlayer player, Component title, String placeholder, UiSession session) {
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new CaptureAnvilMenu(id, inv, session, player, placeholder),
                title));
    }

    public static final class CaptureAnvilMenu extends AnvilMenu {
        private final UiSession session;
        private final ServerPlayer owner;
        private final String placeholder;
        private String last;

        public CaptureAnvilMenu(int id, Inventory inv, UiSession session, ServerPlayer owner, String placeholder) {
            super(id, inv, ContainerLevelAccess.NULL);
            this.session = session;
            this.owner = owner;
            this.placeholder = placeholder;
            ItemStack paper = new ItemStack(Items.PAPER);
            if (placeholder != null && !placeholder.isBlank()) {
                paper.setHoverName(Component.literal(placeholder));
            }
            getSlot(INPUT_SLOT).set(paper);
        }

        /** 客户端每敲一个键都会走到这里；仅记录最新值，提交留到关屏。 */
        @Override
        public boolean setItemName(String name) {
            boolean changed = super.setItemName(name);
            if (changed) last = name;
            return changed;
        }

        /** 占位纸不可被取走，避免结果槽产出/消耗经验与物品。 */
        @Override
        protected boolean mayPickup(Player p, boolean hasStack) {
            return false;
        }

        @Override
        public void removed(Player p) {
            super.removed(p);
            if (!UiSession.isActive(owner.getUUID())) return;
            String v = last;
            UiSession.forget(owner.getUUID());
            // 原版关屏后会紧接着把 containerMenu 复位为背包菜单（doCloseContainer）；
            // 若回调里要再开一个菜单（如二次验密通过后输入新密码），必须延后到下一 tick，
            // 否则新菜单会被那次复位覆盖。
            owner.server.execute(() -> {
                if (v != null && !v.isBlank() && !v.equals(placeholder)) {
                    session.commit(v);
                } else {
                    session.cancel();
                }
            });
        }
    }
}
