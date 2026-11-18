package com.yztc.auth.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.PasswordUtil;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.risk.RiskLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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

    /** 把管理员专属命令统一挂到 {@code /yzts admin ...} 下（OP / LuckPerms / 控制台可用）。 */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth, GateManager gate) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin")
                .requires(s -> !s.isPlayer() || Permissions.isAdmin(s.getPlayer()));

        // 裸 /yzts admin = 打开管理面板
        admin.executes(ctx -> {
            if (gatedOut(ctx.getSource(), auth)) return 0;
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p == null) { ctx.getSource().sendFailure(Lang.msg("in_game_only")); return 0; }
            if (!Permissions.isAdmin(p)) { ctx.getSource().sendFailure(Lang.msg("no_permission")); return 0; }
            com.yztc.auth.ui.AdminPanel.open(p.getServer(), p, auth, gate, ctx.getSource());
            return 1;
        });

        admin.then(Commands.literal("setpassword")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                        : setOtherPassword(ctx.getSource(), auth,
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "password"))))));

        admin.then(Commands.literal("delete")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                : deleteAccount(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));

        admin.then(Commands.literal("forcelogin")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                : forceLogin(ctx.getSource(), auth, StringArgumentType.getString(ctx, "player")))));

        admin.then(Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0
                                : kick(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));

        admin.then(Commands.literal("migrate")
                .requires(s -> s.hasPermission(2) || !s.isPlayer())
                .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0 : MigrateCommand.run(ctx.getSource(), false))
                .then(Commands.literal("--dry-run")
                        .executes(ctx -> gatedOut(ctx.getSource(), auth) ? 0 : MigrateCommand.run(ctx.getSource(), true))));

        admin.then(Commands.literal("serverpassword")
                .then(Commands.literal("set")
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(ctx -> changeGate(ctx.getSource(), auth, gate,
                                        StringArgumentType.getString(ctx, "password"), true))))
                .then(Commands.literal("clear")
                        .executes(ctx -> changeGate(ctx.getSource(), auth, gate, null, false))));

        StatusCommand.attach(admin, auth);
        RiskCommand.attach(admin, auth);
        com.yztc.auth.title.TitleCommand.attachAdmin(admin, auth);

        root.then(admin);
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
        com.yztc.auth.ui.AdminPanel.stepUp(p, auth, Lang.msg(isSet ? "gate_confirm_set" : "gate_confirm_clear"),
                verified -> { if (gateChangeAllowed(true, verified)) applyGate(src, gate, pw, isSet); });
        return 1;
    }

    private static int applyGate(CommandSourceStack src, GateManager gate, String pw, boolean isSet) {
        try {
            if (isSet) {
                gate.set(pw);
                src.sendSuccess(() -> Lang.msg("gate_set_ok"), true);
            } else {
                gate.clear();
                src.sendSuccess(() -> Lang.msg("gate_clear_ok"), true);
            }
            RiskLog.record(isSet ? RiskLog.GATE_SET : RiskLog.GATE_CLEAR, RiskLog.actorOf(src), null, null);
            return 1;
        } catch (IOException e) {
            src.sendFailure(Lang.msg("err_save", e.getMessage()));
            return 0;
        }
    }

    public static int setOtherPassword(CommandSourceStack src, AuthManager auth, String name, String pw) {
        String key = name.toLowerCase(Locale.ROOT);
        Account a = com.yztc.auth.data.StoreHolder.get().get(key);
        if (a == null) { src.sendFailure(Lang.msg("admin_no_account", name)); return 0; }
        Account previous = a.copy();
        PasswordUtil.assign(a, pw, com.yztc.auth.config.AuthConfig.pbkdf2Iterations());
        try { com.yztc.auth.data.StoreHolder.get().save(); } catch (IOException e) {
            com.yztc.auth.data.StoreHolder.get().put(key, previous);
            src.sendFailure(Lang.msg("err_save", e.getMessage())); return 0;
        }
        src.sendSuccess(() -> Lang.msg("admin_reset_ok", name), true);
        RiskLog.record(RiskLog.PASSWORD_RESET, RiskLog.actorOf(src), key, null);
        return 1;
    }

    public static int deleteAccount(CommandSourceStack src, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        var store = com.yztc.auth.data.StoreHolder.get();
        Account previous = store.get(key);
        if (previous == null) {
            src.sendFailure(Lang.msg("admin_no_account", name));
            return 0;
        }
        store.remove(key);
        try { store.save(); } catch (IOException e) {
            store.put(key, previous);
            src.sendFailure(Lang.msg("err_save", e.getMessage()));
            return 0;
        }
        src.sendSuccess(() -> Lang.msg("admin_delete_ok", name), true);
        RiskLog.record(RiskLog.ACCOUNT_DELETE, RiskLog.actorOf(src), key, null);
        // 账号已删除：若其人正在线，直接断开（认证态随退服事件一并清理）
        ServerPlayer online = find(src.getServer(), name);
        if (online != null) online.connection.disconnect(Lang.msg("admin_delete_kick"));
        return 1;
    }

    public static int forceLogin(CommandSourceStack src, AuthManager auth, String name) {
        ServerPlayer target = find(src.getServer(), name);
        if (target == null) { src.sendFailure(Lang.msg("admin_no_online_player", name)); return 0; }
        auth.forceAuthenticate(target);
        target.sendSystemMessage(Lang.msg("admin_force_login"));
        src.sendSuccess(() -> Lang.msg("admin_force_login_ok", name), true);
        RiskLog.record(RiskLog.FORCE_LOGIN, RiskLog.actorOf(src), name.toLowerCase(Locale.ROOT), null);
        return 1;
    }

    public static int kick(CommandSourceStack src, String name) {
        ServerPlayer target = find(src.getServer(), name);
        if (target == null) { src.sendFailure(Lang.msg("admin_no_online_player", name)); return 0; }
        target.connection.disconnect(Lang.msg("admin_kick"));
        src.sendSuccess(() -> Lang.msg("admin_kick_ok", name), true);
        RiskLog.record(RiskLog.KICK, RiskLog.actorOf(src), name.toLowerCase(Locale.ROOT), null);
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
