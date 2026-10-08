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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** `/yztauth mail`：私信、公开留言板、列表与删除。 */
public final class MailCommand {
    private static final int NAME_MAX = 16;

    private MailCommand() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("mail")
                .then(Commands.literal("send")
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
                .then(Commands.literal("board").executes(ctx -> board(ctx.getSource())))
                .then(Commands.literal("del")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> del(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"))))));
    }

    /** 纯逻辑：收件人名是否合法（非空、≤16、且非发件人自己）。 */
    public static boolean validRecipient(String to, String fromLower) {
        if (to == null || to.isBlank() || to.length() > NAME_MAX) return false;
        return !to.toLowerCase(Locale.ROOT).equals(fromLower);
    }

    private static int send(CommandSourceStack src, String to, String text) {
        ServerPlayer p = player(src);
        MailStore store = store();
        if (p == null || store == null) return 0;
        if (text == null || text.isBlank()) { p.sendSystemMessage(Lang.msg("mail_empty")); return 0; }
        String from = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        String toLower = to.toLowerCase(Locale.ROOT);
        if (!validRecipient(to, from)) { p.sendSystemMessage(Lang.msg("mail_no_recipient")); return 0; }
        if (!MailStore.canReceive(store.inboxFor(toLower).size(), AuthConfig.mailMaxPerPlayer())) {
            p.sendSystemMessage(Lang.msg("mail_full")); return 0;
        }
        Mail m = new Mail();
        m.from = from; m.to = toLower; m.text = text;
        m.createdAt = System.currentTimeMillis(); m.isPublic = false;
        store.add(m);
        if (!save(src, store)) return 0;
        p.sendSystemMessage(Lang.msg("mail_sent"));
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
        if (!save(src, store)) return 0;
        p.sendSystemMessage(Lang.msg("mail_posted"));
        return 1;
    }

    private static int list(CommandSourceStack src) {
        ServerPlayer p = player(src);
        MailStore store = store();
        if (p == null || store == null) return 0;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        List<Mail> inbox = store.inboxFor(name);
        int unread = store.unreadFor(name);
        p.sendSystemMessage(Lang.msg("mail_list_header", String.valueOf(unread)));
        if (inbox.isEmpty()) p.sendSystemMessage(Lang.msg("mail_none"));
        else for (Mail m : inbox) p.sendSystemMessage(line(m));
        for (Mail m : inbox) store.markRead(m.id);
        List<Mail> board = store.board();
        p.sendSystemMessage(Lang.msg("mail_board_header"));
        if (board.isEmpty()) p.sendSystemMessage(Lang.msg("mail_none"));
        else for (Mail m : board) p.sendSystemMessage(line(m));
        save(src, store);
        return 1;
    }

    private static int board(CommandSourceStack src) {
        ServerPlayer p = player(src);
        MailStore store = store();
        if (p == null || store == null) return 0;
        List<Mail> board = store.board();
        p.sendSystemMessage(Lang.msg("mail_board_header"));
        if (board.isEmpty()) p.sendSystemMessage(Lang.msg("mail_none"));
        else for (Mail m : board) p.sendSystemMessage(line(m));
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
        store.remove(id);
        save(src, store);
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

    private static boolean save(CommandSourceStack src, MailStore store) {
        try {
            store.save();
            return true;
        } catch (IOException e) {
            src.sendFailure(Component.literal("留言写盘失败：" + e.getMessage()));
            return false;
        }
    }
}
