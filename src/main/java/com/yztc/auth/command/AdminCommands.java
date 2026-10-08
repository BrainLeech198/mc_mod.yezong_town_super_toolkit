package com.yztc.auth.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.PasswordUtil;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Locale;

public final class AdminCommands {
    private AdminCommands() {}

    private static GateManager gateRef;

    /** 装配期注入，供指令注册时取同一份 GateManager。 */
    public static void setGate(GateManager g) { gateRef = g; }

    public static GateManager gate() { return gateRef; }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth, GateManager gate) {
        root.then(Commands.literal("admin").executes(ctx -> {
            if (gatedOut(ctx.getSource(), auth)) return 0;
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            if (!Permissions.isAdmin(p)) { ctx.getSource().sendFailure(Lang.msg("no_permission")); return 0; }
            com.yztc.auth.ui.AdminPanel.open(p.getServer(), p, auth, gate, ctx.getSource());
            return 1;
        }));

        root.then(Commands.literal("migrate")
                .requires(s -> s.hasPermission(2) || !s.isPlayer())
                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0 : MigrateCommand.run(ctx.getSource(), false))
                .then(Commands.literal("--dry-run")
                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0 : MigrateCommand.run(ctx.getSource(), true))));

        root.then(Commands.literal("serverpassword")
                .then(Commands.literal("set")
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(ctx -> changeGate(ctx.getSource(), auth, gate,
                                        StringArgumentType.getString(ctx, "password"), true))))
                .then(Commands.literal("clear")
                        .executes(ctx -> changeGate(ctx.getSource(), auth, gate, null, false))));

        // 控制台可用的管理操作（重置密码/删号/强制登录/踢出）
        root.then(Commands.literal("op")
                .requires(s -> !s.isPlayer() || Permissions.isAdmin(s.getPlayer()))
                .then(Commands.literal("setpassword")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("password", StringArgumentType.greedyString())
                                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                                : setOtherPassword(ctx.getSource(), auth,
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "password"))))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                        : deleteAccount(ctx.getSource(), StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("forcelogin")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                        : forceLogin(ctx.getSource(), auth, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                        : kick(ctx.getSource(), StringArgumentType.getString(ctx, "player"))))));
    }

    /** 未过闸者一律拒绝（含管理员）。返回 true 表示已拒绝并已提示。 */
    private static boolean gatedOut(CommandSourceStack src, AuthManager auth) {
        ServerPlayer p = src.getPlayer();
        return p != null && RootCommand.gateBlocked(p, auth);
    }

    /** 纯逻辑：闸门改动的二次验密策略——游戏内必须验密，控制台（无玩家）免。 */
    public static boolean gateChangeAllowed(boolean sourceIsPlayer, boolean stepUpVerified) {
        return !sourceIsPlayer || stepUpVerified;
    }

    /** 改闸门：游戏内先二次验密（验过才落盘），控制台免。 */
    private static int changeGate(CommandSourceStack src, AuthManager auth, GateManager gate,
                                  String pw, boolean isSet) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return applyGate(src, gate, pw, isSet);
        if (!Permissions.isGateKeeper(p)) { src.sendFailure(Lang.msg("no_permission")); return 0; }
        com.yztc.auth.ui.AdminPanel.stepUp(p, auth, isSet ? "确认修改全服闸门密码" : "确认关闭全服闸门",
                verified -> { if (gateChangeAllowed(true, verified)) applyGate(src, gate, pw, isSet); });
        return 1;
    }

    private static int applyGate(CommandSourceStack src, GateManager gate, String pw, boolean isSet) {
        try {
            if (isSet) {
                gate.set(pw);
                src.sendSuccess(() -> Component.literal("全服闸门密码已设置"), true);
            } else {
                gate.clear();
                src.sendSuccess(() -> Component.literal("全服闸门已关闭"), true);
            }
            return 1;
        } catch (IOException e) {
            src.sendFailure(Component.literal("写盘失败：" + e.getMessage()));
            return 0;
        }
    }

    public static int setOtherPassword(CommandSourceStack src, AuthManager auth, String name, String pw) {
        String key = name.toLowerCase(Locale.ROOT);
        Account a = com.yztc.auth.data.StoreHolder.get().get(key);
        if (a == null) { src.sendFailure(Component.literal("无此账号：" + name)); return 0; }
        PasswordUtil.assign(a, pw, com.yztc.auth.config.AuthConfig.pbkdf2Iterations());
        com.yztc.auth.data.StoreHolder.get().put(key, a);
        try { com.yztc.auth.data.StoreHolder.get().save(); } catch (IOException e) {
            src.sendFailure(Component.literal("写盘失败：" + e.getMessage())); return 0;
        }
        src.sendSuccess(() -> Component.literal("已重置 " + name + " 的密码"), true);
        return 1;
    }

    public static int deleteAccount(CommandSourceStack src, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        boolean removed = com.yztc.auth.data.StoreHolder.get().remove(key);
        try { com.yztc.auth.data.StoreHolder.get().save(); } catch (IOException ignored) {}
        src.sendSuccess(() -> Component.literal(removed ? "已删除账号 " + name : "无此账号：" + name), true);
        return removed ? 1 : 0;
    }

    public static int forceLogin(CommandSourceStack src, AuthManager auth, String name) {
        ServerPlayer target = find(src.getServer(), name);
        if (target == null) { src.sendFailure(Component.literal("玩家不在线：" + name)); return 0; }
        auth.forceAuthenticate(target);
        target.sendSystemMessage(Lang.msg("admin_force_login"));
        src.sendSuccess(() -> Component.literal("已强制 " + name + " 登录"), true);
        return 1;
    }

    public static int kick(CommandSourceStack src, String name) {
        ServerPlayer target = find(src.getServer(), name);
        if (target == null) { src.sendFailure(Component.literal("玩家不在线：" + name)); return 0; }
        target.connection.disconnect(Lang.msg("admin_kick"));
        src.sendSuccess(() -> Component.literal("已踢出 " + name), true);
        return 1;
    }

    private static ServerPlayer find(MinecraftServer server, String name) {
        if (server == null) return null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
