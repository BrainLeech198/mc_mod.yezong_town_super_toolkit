package com.yztc.auth.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Mail;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.ui.ChatPrompt;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** `/yzts mail`：私信、公开留言板、列表与删除。 */
public final class MailCommand {
    private static final int NAME_MAX = 16;

    private MailCommand() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("mail")
                .then(Commands.literal("send")
                        .executes(ctx -> openComposeGui(ctx.getSource()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> send(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "text"))))))
                .then(Commands.literal("post")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> post(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("manage").executes(ctx -> openManageGui(ctx.getSource())))
                .then(Commands.literal("board").executes(ctx -> board(ctx.getSource())))
                .then(Commands.literal("del")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> del(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"))))));
    }

    /** 纯逻辑：收件人名是否合法（非空、≤16、非发件人自己、且已注册）。 */
    public static boolean validRecipient(String to, String fromLower, java.util.Set<String> registered) {
        if (to == null || to.isBlank() || to.length() > NAME_MAX) return false;
        String toLower = to.toLowerCase(Locale.ROOT);
        if (toLower.equals(fromLower)) return false;
        return registered != null && registered.contains(toLower);
    }

    private static int send(CommandSourceStack src, String to, String text) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        return deliver(p, to, text);
    }

    /** 从玩家发起一条私信（命令与 GUI 共用）：含内容/收件人/容量校验与落盘。 */
    public static int deliver(ServerPlayer p, String to, String text) {
        MailStore store = store();
        if (store == null) return 0;
        if (text == null || text.isBlank()) { p.sendSystemMessage(Lang.msg("mail_empty")); return 0; }
        String from = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        String toLower = to.toLowerCase(Locale.ROOT);
        if (!validRecipient(to, from, StoreHolder.get().names())) { p.sendSystemMessage(Lang.msg("mail_no_recipient")); return 0; }
        if (!MailStore.canReceive(
                MailStore.liveCount(store.inboxFor(toLower), System.currentTimeMillis(), AuthConfig.mailExpireDays()),
                AuthConfig.mailMaxPerPlayer())) {
            p.sendSystemMessage(Lang.msg("mail_full")); return 0;
        }
        Mail m = new Mail();
        m.from = from; m.to = toLower; m.text = text;
        m.createdAt = System.currentTimeMillis(); m.isPublic = false;
        store.add(m);
        try {
            store.save();
        } catch (IOException e) {
            store.remove(m.id);
            p.sendSystemMessage(Lang.msg("err_mail_save", e.getMessage()));
            return 0;
        }
        p.sendSystemMessage(Lang.msg("mail_sent"));
        notifyIfOnline(p, toLower);
        return 1;
    }

    /** 收件人在线时给一条提醒 + 可点的查看按钮。 */
    private static void notifyIfOnline(ServerPlayer sender, String toLower) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        ServerPlayer target = null;
        for (ServerPlayer op : server.getPlayerList().getPlayers()) {
            if (op.getGameProfile().getName().equalsIgnoreCase(toLower)) { target = op; break; }
        }
        if (target == null) return;
        target.sendSystemMessage(Lang.msg("mail_notify", sender.getGameProfile().getName()));
        target.sendSystemMessage(ChatPrompt.runButton(Lang.msg("btn_view"), "/yzts mail list"));
        com.yztc.auth.ui.Sounds.prompt(target);
    }

    private static int openComposeGui(CommandSourceStack src) {
        ServerPlayer p = player(src);
        if (p == null) { src.sendFailure(Lang.msg("in_game_only")); return 0; }
        com.yztc.auth.ui.MailPanel.openCompose(p);
        return 1;
    }

    private static int openManageGui(CommandSourceStack src) {
        ServerPlayer p = player(src);
        if (p == null) { src.sendFailure(Lang.msg("in_game_only")); return 0; }
        com.yztc.auth.ui.MailPanel.openManage(p);
        return 1;
    }

    private static int post(CommandSourceStack src, String text) {
        ServerPlayer p = player(src);
        MailStore store = store();
        if (p == null || store == null) return 0;
        if (text == null || text.isBlank()) { p.sendSystemMessage(Lang.msg("mail_empty")); return 0; }
        Mail m = new Mail();
        m.from = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        m.to = ""; m.text = text;
        m.createdAt = System.currentTimeMillis(); m.isPublic = true;
        store.add(m);
        if (!save(p, store)) {
            store.remove(m.id);
            return 0;
        }
        p.sendSystemMessage(Lang.msg("mail_posted"));
        return 1;
    }

    private static int list(CommandSourceStack src) {
        ServerPlayer p = player(src);
        if (p == null) return 0;
        return showList(p);
    }

    /** 供 GUI（个人中心）复用：把收件箱与公开板渲染到聊天栏；私信在此标为已读。 */
    public static int showList(ServerPlayer p) {
        MailStore store = store();
        if (store == null) return 0;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        List<Mail> inbox = store.inboxFor(name);
        int unread = store.unreadFor(name);

        List<Component> lines = new ArrayList<>();
        lines.add(Lang.msg("mail_list_header", String.valueOf(unread)));
        if (inbox.isEmpty()) lines.add(Lang.msg("mail_none"));
        else for (Mail m : inbox) lines.add(line(m));
        List<Long> previousReadAt = inbox.stream().map(m -> m.readAt).toList();
        for (Mail m : inbox) store.markRead(m.id);
        List<Mail> board = store.board();
        lines.add(Lang.msg("mail_board_header"));
        if (board.isEmpty()) lines.add(Lang.msg("mail_none"));
        else for (Mail m : board) lines.add(line(m));
        if (!save(p, store)) {
            for (int i = 0; i < inbox.size(); i++) inbox.get(i).readAt = previousReadAt.get(i);
        }

        for (Component c : com.yztc.auth.ui.ChatBox.box(Lang.msg("mail_box_title"), lines)) {
            p.sendSystemMessage(c);
        }
        return 1;
    }

    private static int board(CommandSourceStack src) {
        ServerPlayer p = player(src);
        return p == null ? 0 : showBoard(p);
    }

    /** 供功能大厅等 GUI 入口复用：显示公开留言板。 */
    public static int showBoard(ServerPlayer p) {
        MailStore store = store();
        if (store == null) return 0;
        List<Mail> board = store.board();
        List<Component> lines = new ArrayList<>();
        if (board.isEmpty()) lines.add(Lang.msg("mail_none"));
        else for (Mail m : board) lines.add(line(m));
        for (Component c : com.yztc.auth.ui.ChatBox.box(Lang.msg("mail_board_header"), lines)) {
            p.sendSystemMessage(c);
        }
        return 1;
    }

    private static int del(CommandSourceStack src, String id) {
        ServerPlayer p = player(src);
        MailStore store = store();
        if (p == null || store == null) return 0;
        Mail m = store.byId(id);
        if (m == null) { p.sendSystemMessage(Lang.msg("mail_del_none")); return 0; }
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        if (!MailStore.canDelete(m, name, Permissions.isAdmin(p))) {
            p.sendSystemMessage(Lang.msg("mail_del_denied")); return 0;
        }
        try {
            if (!store.removeAndSave(id)) { p.sendSystemMessage(Lang.msg("mail_del_none")); return 0; }
        } catch (IOException e) {
            p.sendSystemMessage(Lang.msg("err_mail_save", e.getMessage()));
            return 0;
        }
        p.sendSystemMessage(Lang.msg("mail_del_ok"));
        return 1;
    }

    /** 单条留言的展示行：`#id 发件人：内容`（公开条目同格式）。 */
    private static Component line(Mail m) {
        return Component.literal("#" + m.id + " " + m.from + ": " + m.text)
                .withStyle(m.isPublic ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    private static ServerPlayer player(CommandSourceStack src) { return src.getPlayer(); }
    private static MailStore store() { return StoreHolder.mail(); }

    private static boolean save(ServerPlayer p, MailStore store) {
        try {
            store.save();
            return true;
        } catch (IOException e) {
            p.sendSystemMessage(Lang.msg("err_mail_save", e.getMessage()));
            return false;
        }
    }
}
