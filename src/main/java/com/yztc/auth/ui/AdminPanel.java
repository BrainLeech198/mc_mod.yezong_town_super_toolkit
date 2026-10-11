package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lang.ServerLang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.risk.RiskLog;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * 管理员面板：列出**全部账号**（含离线），不只在线玩家。
 *
 * <p>顶行 6 个操作按钮作用于当前选中的账号，余下三格（6/7/8）放页码与翻页；
 * 下方 4 行按页列出账号，在线用 PLAYER_HEAD、离线用 SKELETON_SKULL（名字置灰）。
 * 需在线的操作（强制登录/强制登出/踢出）对离线账号给出提示；删除账号时若其人正在线，
 * 直接断开连接（{@link AdminCommands#deleteAccount}）。所有危险操作先过二次验密。
 */
public final class AdminPanel {
    private AdminPanel() {}

    private static final int ROWS = 5;
    private static final int SIZE = 9 * ROWS;
    /** 每页账号数：4 行列表（顶行留给操作与翻页）。 */
    public static final int PER_PAGE = 9 * (ROWS - 1);

    public static void open(MinecraftServer server, ServerPlayer viewer, AuthManager auth, GateManager gate, CommandSourceStack src) {
        viewer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new PanelMenu(id, inv, viewer, server, auth, src),
                Lang.msg("admin_panel_title")));
    }

    /** 纯逻辑：按每页容量算总页数（至少 1 页；容量非正时兜底 1 页）。 */
    public static int pageCount(int total, int perPage) {
        if (perPage <= 0) return 1;
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /** 纯逻辑：列表区槽位 → 账号下标（槽 9..44 对应 page*PER_PAGE + (slotId-9)；非列表区返回 -1）。 */
    public static int slotToIndex(int slotId, int page) {
        if (slotId < 9 || slotId >= 9 + PER_PAGE) return -1;
        return page * PER_PAGE + (slotId - 9);
    }

    private static ItemStack named(Item item, Component name) {
        ItemStack st = new ItemStack(item);
        st.setHoverName(name);
        return st;
    }

    /** 面板菜单：全部账号列表 + 顶行操作；危险项先走二次验密。 */
    public static final class PanelMenu extends ChestMenu {
        private final ServerPlayer viewer;
        private final MinecraftServer server;
        private final AuthManager auth;
        private final CommandSourceStack src;
        private final Container container;
        private final List<String> names = new ArrayList<>();
        private int selected = 0;
        private int page = 0;

        PanelMenu(int id, Inventory inv, ServerPlayer viewer, MinecraftServer server,
                  AuthManager auth, CommandSourceStack src) {
            super(MenuType.GENERIC_9x5, id, inv, new SimpleContainer(SIZE), ROWS);
            this.viewer = viewer;
            this.server = server;
            this.auth = auth;
            this.src = src;
            this.container = getContainer();
            refresh();
        }

        /** 每次刷新都从账号库重读（删除后即时消失），并夹紧当前选中/页码。 */
        private void refresh() {
            for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY);
            names.clear();
            names.addAll(StoreHolder.get().names());
            names.sort(String::compareToIgnoreCase);
            if (names.isEmpty()) {
                container.setItem(4, named(Items.BARRIER, Lang.msg("admin_no_accounts")));
                return;
            }
            int pages = pageCount(names.size(), PER_PAGE);
            if (page >= pages) page = pages - 1;
            if (page < 0) page = 0;
            if (selected >= names.size()) selected = names.size() - 1;
            if (selected < 0) selected = 0;

            String key = names.get(selected);
            String disp = displayName(key);
            container.setItem(0, named(Items.NAME_TAG, Lang.msg("admin_btn_view_mail", disp)));
            container.setItem(1, named(Items.PAPER, Lang.msg("admin_btn_reset_pw", disp)));
            container.setItem(2, named(Items.LIME_DYE, Lang.msg("admin_btn_force_login", disp)));
            container.setItem(3, named(Items.GRAY_DYE, Lang.msg("admin_btn_force_logout", disp)));
            container.setItem(4, named(Items.IRON_DOOR, Lang.msg("admin_btn_kick", disp)));
            container.setItem(5, named(Items.TNT, Lang.msg("admin_btn_delete", disp)));

            int from = page * PER_PAGE;
            int to = Math.min(names.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                String k = names.get(i);
                boolean on = online(k) != null;
                MutableComponent label = Component.literal((i == selected ? "▶ " : "  ") + displayName(k));
                if (!on) label.withStyle(ChatFormatting.GRAY);
                container.setItem(9 + (i - from), named(on ? Items.PLAYER_HEAD : Items.SKELETON_SKULL, label));
            }

            if (pages > 1) {
                container.setItem(6, named(Items.PAPER,
                        Lang.msg("admin_page_indicator", String.valueOf(page + 1), String.valueOf(pages))));
                if (page > 0) container.setItem(7, named(Items.ARROW, Lang.msg("admin_btn_prev")));
                if (to < names.size()) container.setItem(8, named(Items.ARROW, Lang.msg("admin_btn_next")));
            }
        }

        /** 按钮式面板：不调用 super，避免按钮物品被取走或背包被搬动；动作延后到下一 tick 执行。 */
        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            if (slotId < 0 || names.isEmpty()) {
                sendAllDataToRemote();
                return;
            }
            // 顶行余格：6=页码指示，7=上一页，8=下一页
            if (slotId == 6) { sendAllDataToRemote(); return; }
            if (slotId == 7) {
                if (page > 0) { page--; selected = page * PER_PAGE; refresh(); }
                Defer.nextTick(viewer, this::sendAllDataToRemote);
                return;
            }
            if (slotId == 8) {
                if ((page + 1) * PER_PAGE < names.size()) { page++; selected = page * PER_PAGE; refresh(); }
                Defer.nextTick(viewer, this::sendAllDataToRemote);
                return;
            }

            Sounds.click(viewer);
            String key = names.get(selected);
            String disp = displayName(key);
            switch (slotId) {
                case 0 -> Defer.nextTick(viewer, () -> confirm(Lang.msg("admin_confirm_view_mail", disp),
                        () -> src.sendSuccess(() -> Lang.msg("admin_mail_is", emailOf(key)), false)));
                case 1 -> Defer.nextTick(viewer, () -> confirm(Lang.msg("admin_confirm_reset_pw", disp),
                        () -> AnvilInput.open(viewer, Lang.msg("admin_new_password_title"),
                                UiSession.open(viewer.getUUID(),
                                        pw -> {
                                            AdminCommands.setOtherPassword(src, auth, disp, pw);
                                            viewer.sendSystemMessage(Lang.msg("changepw_ok"));
                                        },
                                        () -> viewer.sendSystemMessage(Lang.msg("confirm_bad"))))));
                case 2 -> Defer.nextTick(viewer, () -> requireOnline(key, disp, () -> confirm(Lang.msg("admin_confirm_force_login", disp),
                        () -> AdminCommands.forceLogin(src, auth, disp))));
                case 3 -> Defer.nextTick(viewer, () -> requireOnline(key, disp, () -> confirm(Lang.msg("admin_confirm_force_logout", disp),
                        () -> {
                            ServerPlayer t = online(key);
                            if (t == null) return;
                            auth.forceLogout(t, AuthConfig.logoutMode());
                            t.sendSystemMessage(Lang.msg("admin_force_logout"));
                            RiskLog.record(RiskLog.FORCE_LOGOUT, actor(), key, null);
                        })));
                case 4 -> Defer.nextTick(viewer, () -> requireOnline(key, disp, () -> confirm(Lang.msg("admin_confirm_kick", disp),
                        () -> AdminCommands.kick(src, disp))));
                case 5 -> Defer.nextTick(viewer, () -> confirm(Lang.msg("admin_confirm_delete", disp),
                        () -> AdminCommands.deleteAccount(src, disp)));
                default -> {
                    int idx = slotToIndex(slotId, page);
                    if (idx >= 0 && idx < names.size()) {
                        selected = idx;
                        refresh();
                    }
                    Defer.nextTick(viewer, this::sendAllDataToRemote);
                }
            }
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return ItemStack.EMPTY;
        }

        /** 仅在线可用的操作：离线账号直接提示，不进入验密。 */
        private void requireOnline(String key, String disp, Runnable action) {
            if (online(key) == null) {
                viewer.sendSystemMessage(Lang.msg("admin_no_online_player", disp));
                sendAllDataToRemote();
                return;
            }
            action.run();
        }

        private ServerPlayer online(String keyLower) {
            if (server == null) return null;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getGameProfile().getName().equalsIgnoreCase(keyLower)) return p;
            }
            return null;
        }

        /** 展示名：在线取档案名；否则取下线快照里的原名；再退回归一化键。 */
        private String displayName(String key) {
            ServerPlayer on = online(key);
            if (on != null) return on.getGameProfile().getName();
            LastSeenStore ls = StoreHolder.lastSeen();
            LastSeen s = ls == null ? null : ls.get(key);
            if (s != null && s.name != null && !s.name.isBlank()) return s.name;
            return key;
        }

        private String emailOf(String key) {
            Account a = StoreHolder.get().get(key);
            return a == null || a.email == null ? ServerLang.template("admin_email_unset") : a.email;
        }

        private String actor() {
            return viewer.getGameProfile().getName().toLowerCase(Locale.ROOT);
        }

        /** 危险操作：先开铁砧让操作者输自己密码，验过再执行。 */
        private void confirm(Component hint, Runnable action) {
            stepUp(viewer, auth, hint, ok -> { if (ok) action.run(); });
        }
    }

    /**
     * 二次验密（游戏内危险操作共用）：开铁砧让操作者输入自己的密码，
     * 把验密结果回传给 then。取消或验密失败都回传 false。
     */
    public static void stepUp(ServerPlayer viewer, AuthManager auth, Component hint, java.util.function.Consumer<Boolean> then) {
        if (!viewer.isAlive()) { then.accept(false); return; }
        viewer.sendSystemMessage(hint);
        viewer.sendSystemMessage(Lang.msg("confirm_prompt"));
        AnvilInput.open(viewer, Lang.msg("confirm_prompt"),
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
