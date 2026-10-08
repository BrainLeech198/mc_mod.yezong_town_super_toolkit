package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OP 管理面板：6 行箱子。顶行 6 个操作按钮（作用于当前选中的在线玩家），
 * 下方列出在线玩家供点击切换选中。所有操作都要先过二次验密（输入操作者自己的密码）。
 */
public final class AdminPanel {
    private AdminPanel() {}

    private static final int ROWS = 6;
    private static final int SIZE = 9 * ROWS;

    public static void open(MinecraftServer server, ServerPlayer viewer, AuthManager auth, GateManager gate, CommandSourceStack src) {
        List<ServerPlayer> online = new ArrayList<>(server.getPlayerList().getPlayers());
        viewer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PanelMenu(id, inv, viewer, server, auth, gate, src, online),
                Component.literal("yztauth 管理面板")));
    }

    private static ItemStack named(Item item, String name) {
        ItemStack st = new ItemStack(item);
        st.setHoverName(Component.literal(name));
        return st;
    }

    /** 面板菜单：拦点击做按钮分派，危险项先走二次验密。 */
    public static final class PanelMenu extends ChestMenu {
        private final ServerPlayer viewer;
        private final AuthManager auth;
        private final CommandSourceStack src;
        private final List<ServerPlayer> online;
        private final Container container;
        private int selected = 0;

        PanelMenu(int id, Inventory inv, ServerPlayer viewer, MinecraftServer server,
                  AuthManager auth, GateManager gate, CommandSourceStack src, List<ServerPlayer> online) {
            super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(SIZE), ROWS);
            this.viewer = viewer;
            this.auth = auth;
            this.src = src;
            this.online = online;
            this.container = getContainer();
            refresh();
        }

        private void refresh() {
            for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY);
            if (online.isEmpty()) {
                container.setItem(4, named(Items.BARRIER, "当前无人在线"));
                return;
            }
            ServerPlayer sel = online.get(selected);
            String n = sel.getGameProfile().getName();
            container.setItem(0, named(Items.NAME_TAG, "查看邮箱: " + n));
            container.setItem(1, named(Items.PAPER, "重置密码: " + n));
            container.setItem(2, named(Items.LIME_DYE, "强制登录: " + n));
            container.setItem(3, named(Items.GRAY_DYE, "强制登出: " + n));
            container.setItem(4, named(Items.IRON_DOOR, "踢出: " + n));
            container.setItem(5, named(Items.TNT, "删除账号: " + n));

            int max = Math.min(online.size(), SIZE - 9);
            for (int i = 0; i < max; i++) {
                ServerPlayer pl = online.get(i);
                container.setItem(9 + i, named(Items.PLAYER_HEAD,
                        (i == selected ? "▶ " : "  ") + pl.getGameProfile().getName()));
            }
        }

        /** 按钮式面板：不调用 super，避免按钮物品被取走或背包被搬动。 */
        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            if (slotId < 0 || online.isEmpty()) {
                sendAllDataToRemote();
                return;
            }
            ServerPlayer sel = online.get(selected);
            String name = sel.getGameProfile().getName();
            switch (slotId) {
                case 0 -> confirm("确认查看 " + name + " 的邮箱",
                        () -> src.sendSuccess(() -> Component.literal("邮箱=" + emailOf(sel)), false));
                case 1 -> confirm("确认重置 " + name + " 的密码",
                        () -> AnvilInput.open(viewer, Component.literal("输入新密码"), "新密码",
                                UiSession.open(viewer.getUUID(),
                                        pw -> {
                                            AdminCommands.setOtherPassword(src, auth, name, pw);
                                            viewer.sendSystemMessage(Lang.msg("changepw_ok"));
                                        },
                                        () -> viewer.sendSystemMessage(Lang.msg("confirm_bad")))));
                case 2 -> confirm("确认强制登录 " + name,
                        () -> AdminCommands.forceLogin(src, auth, name));
                case 3 -> confirm("确认强制登出 " + name,
                        () -> {
                            auth.forceLogout(sel, AuthConfig.logoutMode());
                            sel.sendSystemMessage(Lang.msg("admin_force_logout"));
                        });
                case 4 -> confirm("确认踢出 " + name,
                        () -> AdminCommands.kick(src, name));
                case 5 -> confirm("确认删除 " + name + " 的账号",
                        () -> AdminCommands.deleteAccount(src, name));
                default -> {
                    int idx = slotId - 9;
                    if (idx >= 0 && idx < online.size()) {
                        selected = idx;
                        refresh();
                    }
                    sendAllDataToRemote();
                }
            }
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return ItemStack.EMPTY;
        }

        private String emailOf(ServerPlayer p) {
            Account a = StoreHolder.get().get(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
            return a == null || a.email == null ? "(未设置)" : a.email;
        }

        /** 危险操作：先开铁砧让操作者输自己密码，验过再执行。 */
        private void confirm(String hint, Runnable action) {
            stepUp(viewer, auth, hint, ok -> { if (ok) action.run(); });
        }
    }

    /**
     * 二次验密（游戏内危险操作共用）：开铁砧让操作者输入自己的密码，
     * 把验密结果回传给 then。取消或验密失败都回传 false。
     */
    public static void stepUp(ServerPlayer viewer, AuthManager auth, String hint, java.util.function.Consumer<Boolean> then) {
        if (!viewer.isAlive()) { then.accept(false); return; }
        viewer.sendSystemMessage(Component.literal(hint));
        viewer.sendSystemMessage(Lang.msg("confirm_prompt"));
        AnvilInput.open(viewer, Lang.msg("confirm_prompt"), "你的密码",
                UiSession.open(viewer.getUUID(), pw -> {
                    boolean ok = auth.verifyOwnPassword(viewer, pw)
                            || Permissions.has(viewer, Permissions.BYPASS_CONFIRM, 5);
                    viewer.sendSystemMessage(Lang.msg(ok ? "confirm_ok" : "confirm_bad"));
                    then.accept(ok);
                }, () -> {
                    viewer.sendSystemMessage(Lang.msg("confirm_bad"));
                    then.accept(false);
                }));
    }
}
