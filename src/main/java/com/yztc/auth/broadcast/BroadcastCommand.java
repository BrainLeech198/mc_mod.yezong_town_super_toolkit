package com.yztc.auth.broadcast;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.command.RootCommand;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * {@code /yzts say}（管理员公告，走全通道）与 {@code /yzts shout}（玩家喊话，仅聊天栏+冷却）。
 * 裸命令开铁砧输入 GUI；带内容直接发送。
 */
public final class BroadcastCommand {
    private BroadcastCommand() {}

    /** 发送前置判定结果：errorKey 非空表示拒绝（对应提示键），否则 content 为净化后的内容。 */
    public record Prepared(String errorKey, String content) {}

    /** 纯逻辑：空/超长/净化统一判定——命令与 GUI 共用，保证两条路径一致。 */
    public static Prepared prepare(String text, boolean allowColors, int max) {
        String content = Broadcaster.sanitize(text, allowColors, max);
        if (content.isEmpty()) return new Prepared("broadcast_empty", "");
        if (text != null && text.trim().length() > max) return new Prepared("broadcast_too_long", "");
        return new Prepared(null, content);
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("say")
                .requires(s -> !s.isPlayer() || Permissions.isAdmin(s.getPlayer()))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) return directSay(ctx.getSource(), null);
                    if (RootCommand.gateBlocked(p, auth)) return 0;
                    if (!Permissions.isAdmin(p)) { ctx.getSource().sendFailure(Lang.msg("no_permission")); return 0; }
                    BroadcastPrompt.openSay(p);
                    return 1;
                })
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> directSay(ctx.getSource(), StringArgumentType.getString(ctx, "text")))));

        root.then(Commands.literal("shout")
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendFailure(Lang.msg("in_game_only")); return 0; }
                    if (RootCommand.gateBlocked(p, auth)) return 0;
                    if (!auth.isAuthenticated(p)) { p.sendSystemMessage(Lang.msg("need_login")); return 0; }
                    BroadcastPrompt.openShout(p);
                    return 1;
                })
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendFailure(Lang.msg("in_game_only")); return 0; }
                            return performShout(p, StringArgumentType.getString(ctx, "text"));
                        })));
    }

    /** 控制台或带参的公告。text 为 null 时（控制台裸命令）给失败提示。 */
    private static int directSay(CommandSourceStack src, String text) {
        ServerPlayer p = src.getPlayer();
        int max = AuthConfig.broadcastMaxLength();
        Prepared pr = prepare(text, true, max);
        if (pr.errorKey() != null) { src.sendFailure(Lang.msg(pr.errorKey(), max)); return 0; }
        Set<Broadcaster.Channel> ch = Broadcaster.channelsFor(true,
                AuthConfig.broadcastChannelChat(), AuthConfig.broadcastChannelTitle(),
                AuthConfig.broadcastChannelSubtitle(), AuthConfig.broadcastChannelActionbar());
        Broadcaster.send(src.getServer(), ch, AuthConfig.broadcastPrefix(),
                p == null ? null : p.getGameProfile().getName(), pr.content());
        if (p != null) p.sendSystemMessage(Lang.msg("broadcast_sent"));
        else src.sendSuccess(() -> Lang.msg("broadcast_sent"), true);
        return 1;
    }

    /** 管理员公告（GUI 入口）。 */
    public static int performSay(ServerPlayer p, String text) {
        int max = AuthConfig.broadcastMaxLength();
        Prepared pr = prepare(text, true, max);
        if (pr.errorKey() != null) { p.sendSystemMessage(Lang.msg(pr.errorKey(), max)); return 0; }
        Set<Broadcaster.Channel> ch = Broadcaster.channelsFor(true,
                AuthConfig.broadcastChannelChat(), AuthConfig.broadcastChannelTitle(),
                AuthConfig.broadcastChannelSubtitle(), AuthConfig.broadcastChannelActionbar());
        Broadcaster.send(p.getServer(), ch, AuthConfig.broadcastPrefix(), null, pr.content());
        p.sendSystemMessage(Lang.msg("broadcast_sent"));
        return 1;
    }

    /** 玩家喊话：先冷却（管理员豁免），后净化+发送（仅聊天栏）。GUI 与命令共用。 */
    public static int performShout(ServerPlayer p, String text) {
        MinecraftServer server = p.getServer();
        long now = System.currentTimeMillis();
        boolean admin = Permissions.isAdmin(p);
        long wait = Broadcaster.remainingFor(p.getUUID(), now, admin);
        if (wait > 0) { p.sendSystemMessage(Lang.msg("broadcast_cooldown", wait)); return 0; }
        int max = AuthConfig.broadcastMaxLength();
        Prepared pr = prepare(text, AuthConfig.broadcastAllowPlayerColors(), max);
        if (pr.errorKey() != null) { p.sendSystemMessage(Lang.msg(pr.errorKey(), max)); return 0; }
        if (!admin) Broadcaster.markShout(p.getUUID(), now);
        Broadcaster.send(server, Broadcaster.channelsFor(false, false, false, false, false),
                AuthConfig.broadcastPrefix(), p.getGameProfile().getName(), pr.content());
        p.sendSystemMessage(Lang.msg("broadcast_sent"));
        return 1;
    }
}
