package com.yztc.auth.ui;

import com.yztc.auth.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 用铁砧改名框接收一段文本。
 *
 * <p>操作只有两步：点左侧格子里的占位纸，然后在名字框输入，输完按 Esc（或直接关屏）提交。
 * 结果槽恒空，框里没有任何需要点的东西。
 *
 * <p>原版铁砧在输入槽为空时不会发送改名包（{@code AnvilScreen.onNameChanged} 见到空槽即返回），
 * 且名字框仅在槽内有物品时才可编辑，故槽内放一张占位纸。占位纸的自定义名随输入同步更新：
 * 客户端 {@code AnvilScreen.slotChanged} 会用槽 0 的 hover 名重置输入框，同步即可让重填等于原输入，
 * 而不是把玩家敲的字抹掉。
 *
 * <p>改名包是逐键发送的，故每次 {@code setItemName} 都无条件记下最新整串，提交判定在关屏（{@link #removed}）。
 */
public final class AnvilInput {
    private AnvilInput() {}

    public static void open(ServerPlayer player, Component title, UiSession session) {
        Sounds.prompt(player); // 弹框提示音，提醒玩家该输入了
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new CaptureAnvilMenu(id, inv, session, player),
                title));
    }

    /** 纯逻辑：关屏时是否提交——有非空输入即提交（Esc 亦可），空白才取消。 */
    public static boolean shouldCommit(String last) {
        return last != null && !last.isBlank();
    }

    public static final class CaptureAnvilMenu extends AnvilMenu {
        private final UiSession session;
        private final ServerPlayer owner;
        private String last;

        public CaptureAnvilMenu(int id, Inventory inv, UiSession session, ServerPlayer owner) {
            super(id, inv, ContainerLevelAccess.NULL);
            this.session = session;
            this.owner = owner;
            ItemStack paper = new ItemStack(Items.PAPER);
            paper.setHoverName(Component.literal(""));
            getSlot(INPUT_SLOT).set(paper);
        }

        /** 客户端每敲一个键都会走到这里；记录最新整串，并让占位纸 hover 名与之同步。 */
        @Override
        public boolean setItemName(String name) {
            boolean changed = super.setItemName(name);
            if (name != null) {
                last = name;
                ItemStack in = getSlot(INPUT_SLOT).getItem();
                if (!in.isEmpty()) in.setHoverName(Component.literal(name));
            }
            return changed;
        }

        /** 结果槽恒空：本框只用输入槽收文本，不产出物品（避免出现可点却取不走的回显纸）。 */
        @Override
        public void createResult() {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.setMaximumCost(0);
        }

        /** 三个槽的点击一律拦下并回滚客户端预测：占位纸取不走，结果槽本来也没东西。 */
        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            if (slotId == INPUT_SLOT || slotId == ADDITIONAL_SLOT || slotId == RESULT_SLOT) {
                sendAllDataToRemote();
                return;
            }
            super.clicked(slotId, button, type, p);
        }

        /** 禁用 shift 快捷移动，避免占位纸流入背包。 */
        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return ItemStack.EMPTY;
        }

        /** 占位纸不可被取走。 */
        @Override
        protected boolean mayPickup(Player p, boolean hasStack) {
            return false;
        }

        /**
         * 关屏到这里收尾：有非空输入即提交（Esc 或直接关屏均可）；空白输入关屏 = 取消并提示。
         *
         * <p>刻意不调用 {@code super.removed}：{@code ItemCombinerMenu.removed} 会
         * {@code clearContainer(inputSlots)}，把占位纸塞进玩家背包；此处改为直接丢弃。
         */
        @Override
        public void removed(Player p) {
            this.inputSlots.clearContent();
            this.resultSlots.clearContent();
            if (!UiSession.isActive(owner.getUUID())) return;
            String v = last;
            UiSession.forget(owner.getUUID());
            // 原版关屏后紧接着会把 containerMenu 复位为背包菜单（doCloseContainer）；
            // 回调里常要再开一个菜单（如二次验密通过后输入新密码），必须延后到下一 tick，
            // 否则新菜单会被那次复位覆盖。用 Defer.nextTick（server.execute 在主线程上是同步的）。
            Defer.nextTick(owner, () -> {
                if (shouldCommit(v)) {
                    session.commit(v);
                } else {
                    owner.sendSystemMessage(Lang.msg("anvil_cancelled"));
                    session.cancel();
                }
            });
        }
    }
}
