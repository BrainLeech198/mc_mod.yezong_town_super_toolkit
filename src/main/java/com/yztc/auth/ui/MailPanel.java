package com.yztc.auth.ui;

import com.yztc.auth.command.MailCommand;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.Mail;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lang.ServerLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 邮箱面板（GUI）：写信（分页选人 + 手输名字）、管理（收件箱 → 点开详情 → 删除/返回）。
 */
public final class MailPanel {
    private MailPanel() {}

    private static final int ROWS = 5;
    private static final int SIZE = 9 * ROWS;
    /** 写信选人每页容量：4 行列表（顶行留给「手输名字」与翻页）。 */
    public static final int PER_PAGE = 9 * (ROWS - 1);

    // ===== 写信 =====

    /** 写信：分页列出全部已注册玩家（在线带头像、离线灰名），顶行另有「手输名字」。 */
    public static void openCompose(ServerPlayer viewer) {
        viewer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ComposeMenu(id, inv, viewer),
                Lang.msg("mail_compose_title")));
    }

    private static void composeTo(ServerPlayer v, String to) {
        InputPrompt.choose(v,
                Lang.msg("input_choose"),
                Lang.msg("mail_compose_to", to),
                (player, text) -> MailCommand.deliver(player, to, text) > 0
                        ? AnvilInput.Response.close()
                        : AnvilInput.Response.keep(null),
                "/yzts mail send " + to + " ");
    }

    /** 「手输名字」：先输收件人名，再输正文。 */
    private static void composeManual(ServerPlayer v) {
        AnvilInput.openFlow(v, Lang.msg("mail_compose_manual_title"), Lang.msg("mail_compose_manual_title"), (player, to) -> {
            String from = v.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (!MailCommand.validRecipient(to, from, StoreHolder.get().names())) {
                v.sendSystemMessage(Lang.msg("mail_no_recipient"));
                return AnvilInput.Response.retry(Lang.msg("mail_compose_manual_title"));
            }
            return AnvilInput.Response.next(Lang.msg("mail_compose_to", to), (ignoredPlayer, text) -> {
                int sent = MailCommand.deliver(v, to, text);
                if (sent > 0) return AnvilInput.Response.close();
                return AnvilInput.Response.keep(null);
            });
        }, () -> {});
    }

    /** 写信选人菜单：全部已注册玩家分页 + 顶行「手输名字」。 */
    private static final class ComposeMenu extends ChestMenu {
        private final ServerPlayer viewer;
        private final Container container;
        private final List<String> names = new ArrayList<>();
        private int page = 0;

        ComposeMenu(int id, Inventory inv, ServerPlayer viewer) {
            super(MenuType.GENERIC_9x5, id, inv, new SimpleContainer(SIZE), ROWS);
            this.viewer = viewer;
            this.container = getContainer();
            refresh();
        }

        /** 每次刷新都从账号库重读，排除自己，并夹紧页码。 */
        private void refresh() {
            for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY);
            names.clear();
            String self = viewer.getGameProfile().getName();
            for (String n : StoreHolder.get().names()) {
                if (!n.equalsIgnoreCase(self)) names.add(n);
            }
            names.sort(String::compareToIgnoreCase);

            container.setItem(0, named(Items.NAME_TAG, Lang.msg("mail_compose_manual")));
            if (names.isEmpty()) {
                container.setItem(4, named(Items.BARRIER, Lang.msg("mail_none")));
                return;
            }
            int pages = AdminPanel.pageCount(names.size(), PER_PAGE);
            if (page >= pages) page = pages - 1;
            if (page < 0) page = 0;

            int from = page * PER_PAGE;
            int to = Math.min(names.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                String k = names.get(i);
                boolean on = online(k) != null;
                MutableComponent label = Component.literal(displayName(k));
                if (!on) label.withStyle(ChatFormatting.GRAY);
                container.setItem(9 + (i - from),
                        named(on ? Items.PLAYER_HEAD : Items.SKELETON_SKULL, label));
            }
            if (pages > 1) {
                container.setItem(6, named(Items.PAPER,
                        Lang.msg("admin_page_indicator", String.valueOf(page + 1), String.valueOf(pages))));
                if (page > 0) container.setItem(7, named(Items.ARROW, Lang.msg("btn_prev")));
                if (to < names.size()) container.setItem(8, named(Items.ARROW, Lang.msg("btn_next")));
            }
        }

        /** 按钮式面板：不调用 super，避免物品被取走；开新菜单的动作延后一 tick。 */
        @Override
        public void clicked(int slotId, int button, ClickType type, Player p) {
            if (slotId < 0) { sendAllDataToRemote(); return; }
            if (slotId == 6) { sendAllDataToRemote(); return; }
            if (slotId == 7) {
                if (page > 0) { page--; refresh(); }
                sendAllDataToRemote();
                return;
            }
            if (slotId == 8) {
                if ((page + 1) * PER_PAGE < names.size()) { page++; refresh(); }
                sendAllDataToRemote();
                return;
            }
            if (slotId == 0) {
                Sounds.click(viewer);
                Defer.nextTick(viewer, () -> composeManual(viewer));
                return;
            }
            if (slotId >= 9 && !names.isEmpty()) {
                int idx = page * PER_PAGE + (slotId - 9);
                if (idx >= 0 && idx < names.size()) {
                    Sounds.click(viewer);
                    String to = displayName(names.get(idx));
                    Defer.nextTick(viewer, () -> composeTo(viewer, to));
                }
            }
            sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            return ItemStack.EMPTY;
        }

        private ServerPlayer online(String keyLower) {
            for (ServerPlayer pl : viewer.server.getPlayerList().getPlayers()) {
                if (pl.getGameProfile().getName().equalsIgnoreCase(keyLower)) return pl;
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
    }

    // ===== 管理（收件箱）=====

    /** 管理：列出自己收到的私信（未读带标记与光效），点一条进入详情页。 */
    public static void openManage(ServerPlayer viewer) {
        MailStore store = StoreHolder.mail();
        String name = viewer.getGameProfile().getName().toLowerCase(Locale.ROOT);
        List<Mail> inbox = store == null ? List.of() : store.inboxFor(name);
        List<ButtonPanel.Btn> bs = new ArrayList<>();
        for (Mail m : inbox) {
            boolean unread = m.readAt == 0;
            String id = m.id;
            bs.add(new ButtonPanel.Btn(Items.PAPER, listLabel(m, unread),
                    v -> openDetail(v, id), ButtonPanel.AUTO, unread));
        }
        if (bs.isEmpty()) {
            bs.add(new ButtonPanel.Btn(Items.BARRIER, Lang.msg("mail_none"), null));
        }
        ButtonPanel.open(viewer, Lang.msg("mail_manage_title"), bs);
    }

    /** 详情页：四行只读信息（发件人 / 收件人 / 时间 / 内容）+「删除」（槽 9）+「返回列表」（槽 11）。 */
    public static void openDetail(ServerPlayer viewer, String id) {
        MailStore store = StoreHolder.mail();
        Mail m = store == null ? null : store.byId(id);
        if (m == null) {
            viewer.sendSystemMessage(Lang.msg("mail_del_none"));
            openManage(viewer);
            return;
        }
        if (!m.isPublic && m.readAt == 0) {
            long previousReadAt = m.readAt;
            store.markRead(m.id);
            try {
                store.save();
            } catch (IOException e) {
                m.readAt = previousReadAt;
                viewer.sendSystemMessage(Lang.msg("err_mail_save", e.getMessage()));
            }
        }
        List<Component> info = detailInfo(m, ZoneId.systemDefault());
        List<ButtonPanel.Btn> bs = new ArrayList<>();
        for (int i = 0; i < info.size(); i++) {
            bs.add(new ButtonPanel.Btn(Items.PAPER, info.get(i), null, i));
        }
        bs.add(new ButtonPanel.Btn(Items.TNT, Lang.msg("btn_delete"), v -> delete(v, id), 9));
        bs.add(new ButtonPanel.Btn(Items.ARROW, Lang.msg("btn_back"), MailPanel::openManage, 11));
        ButtonPanel.open(viewer, Lang.msg("mail_detail_title", id), bs);
    }

    private static void delete(ServerPlayer v, String id) {
        MailStore store = StoreHolder.mail();
        try {
            if (store == null || !store.removeAndSave(id)) {
                v.sendSystemMessage(Lang.msg("mail_del_none"));
                openManage(v);
                return;
            }
        } catch (IOException e) {
            v.sendSystemMessage(Lang.msg("err_mail_save", e.getMessage()));
            openManage(v);
            return;
        }
        v.sendSystemMessage(Lang.msg("mail_del_ok"));
        openManage(v);
    }

    // ===== 纯逻辑 =====

    /** 纯逻辑：列表条目标签（未读前置金色「[未读]」标记；正文公开条目青色、私信灰色）。 */
    public static Component listLabel(Mail m, boolean unread) {
        MutableComponent label = Component.empty();
        if (unread) {
            label.append(Component.literal(ServerLang.template("mail_unread_tag") + " ")
                    .withStyle(ChatFormatting.GOLD));
        }
        label.append(Component.literal("#" + m.id + " " + m.from + ": " + m.text)
                .withStyle(m.isPublic ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        return label;
    }

    /** 纯逻辑：一条留言 → 四行详情（发件人 / 收件人 / 时间 / 内容；空值回退「—」）。 */
    public static List<Component> detailInfo(Mail m, ZoneId zone) {
        List<Component> out = new ArrayList<>();
        out.add(Lang.msg("mail_row_from", orDash(m.from)));
        out.add(Lang.msg("mail_row_to", orDash(m.to)));
        out.add(Lang.msg("mail_row_time", RiskPanel.timeText(m.createdAt, zone)));
        out.add(Lang.msg("mail_row_text", orDash(m.text)));
        return out;
    }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static ItemStack named(Item item, Component label) {
        ItemStack st = new ItemStack(item);
        st.setHoverName(label);
        return st;
    }
}
