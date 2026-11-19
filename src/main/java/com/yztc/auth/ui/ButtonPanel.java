package com.yztc.auth.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * 通用「按钮面板」：箱子界面里每个槽放一个不可拾取的图标物品，点击即触发对应动作。
 *
 * <p>与 {@link AdminPanel} 同一套路：{@code clicked} 不调用 super，物品取不走；
 * 动作延后一 tick 执行，便于动作里再开新菜单（如铁砧输入）。
 *
 * <p>槽位：{@link Btn#slot()} 为 {@link #AUTO} 时按出现顺序自动排布，>=0 时落到指定槽
 * （用于把说明行与操作按钮分区摆放）。{@code action} 为 {@code null} 的按钮是「只读项」，
 * 只展示、点击无反应也不关闭界面。
 */
public final class ButtonPanel {
    private ButtonPanel() {}

    /** 自动槽位：按列表顺序排布。 */
    public static final int AUTO = -1;

    /** 一个按钮：图标 + 文字 + 点击动作（{@code null} = 只读展示）；{@code glint} 给图标加附魔光效（如「未读」）。 */
    public record Btn(Item icon, Component label, Consumer<ServerPlayer> action, int slot, boolean glint) {
        public Btn(Item icon, Component label, Consumer<ServerPlayer> action) {
            this(icon, label, action, AUTO, false);
        }
        public Btn(Item icon, Component label, Consumer<ServerPlayer> action, int slot) {
            this(icon, label, action, slot, false);
        }
    }

    /** 纯逻辑：按钮数 → 箱子行数（1..6）。 */
    public static int rowsFor(int count) {
        if (count <= 0) return 1;
        int rows = (count + 8) / 9;
        return Math.min(6, Math.max(1, rows));
    }

    /** 纯逻辑：按钮集 → 箱子行数（取自动槽位顺序数与最大显式槽位，二者较大者）。 */
    public static int requiredRows(List<Btn> buttons) {
        int next = 0;
        int max = -1;
        for (Btn b : buttons) {
            int s = b.slot() >= 0 ? b.slot() : next++;
            if (s > max) max = s;
        }
        return rowsFor(Math.max(max + 1, next));
    }

    public static void open(ServerPlayer viewer, Component title, List<Btn> buttons) {
        List<Btn> use = buttons.size() > 54 ? buttons.subList(0, 54) : buttons;
        int rows = requiredRows(use);
        viewer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new Menu(id, inv, viewer, use, rows),
                title));
    }

    private static ItemStack named(Item item, Component label) {
        ItemStack st = new ItemStack(item);
        st.setHoverName(label);
        return st;
    }

    /** 按钮图标：需要时叠一层附魔光效，并把附魔名从悬浮提示里隐去。 */
    private static ItemStack named(Btn b) {
        ItemStack st = named(b.icon(), b.label());
        if (b.glint()) {
            st.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            st.hideTooltipPart(ItemStack.TooltipPart.ENCHANTMENTS);
        }
        return st;
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @SuppressWarnings("unchecked")
    private static final class Menu extends ChestMenu {
        private final ServerPlayer viewer;
        private final java.util.Map<Integer, Btn> bySlot = new java.util.HashMap<>();
        private final int slotCount;

        Menu(int id, Inventory inv, ServerPlayer viewer, List<Btn> buttons, int rows) {
            super((MenuType<ChestMenu>) typeFor(rows), id, inv, new SimpleContainer(rows * 9), rows);
            this.viewer = viewer;
            this.slotCount = rows * 9;
            var container = getContainer();
            int next = 0;
            for (Btn b : buttons) {
                int s = b.slot() >= 0 ? b.slot() : next++;
                if (s < 0 || s >= slotCount) continue;
                bySlot.put(s, b);
                container.setItem(s, named(b));
            }
        }

        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            Btn b = bySlot.get(slotId);
            if (b == null || b.action() == null) { // 空槽或只读项：不取物、不关闭
                sendAllDataToRemote();
                return;
            }
            Sounds.click(viewer);
            // 动作**必须延后到下一 tick**：先关掉当前箱子菜单，再执行后续动作，
            // 避免聊天提示出现时旧菜单仍挡在屏幕上；若动作打开新菜单，也不会被旧菜单收尾关掉。
            // 用 Defer.nextTick 而非 server.execute —— 后者在主线程上是同步执行的。
            Defer.nextTick(viewer, () -> {
                if (viewer.containerMenu != this) return;
                viewer.closeContainer();
                b.action().accept(viewer);
            });
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return ItemStack.EMPTY;
        }
    }
}
