package com.yztc.auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.ui.AccountPanel;
import com.yztc.auth.ui.AuthGui;
import com.yztc.auth.ui.EmailFlow;
import com.yztc.auth.ui.LogoutPrompt;
import com.yztc.auth.ui.WelcomeSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class RootCommand {
    private RootCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, AuthManager auth, GateManager gate) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("yzts");

        root.then(Commands.literal("gate")
                .executes(ctx -> runPlayer(ctx.getSource(), p -> {
                    if (!gate.isGateSet()) { p.sendSystemMessage(Lang.msg("gate_not_set")); return 1; }
                    AuthGui.openGate(p, auth);
                    return 1;
                }))
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runPlayer(ctx.getSource(), p ->
                                doGate(p, auth, StringArgumentType.getString(ctx, "password"))))));

        root.then(Commands.literal("login")
                .executes(ctx -> runGated(ctx.getSource(), auth, p -> {
                    AuthGui.openLoginOrRegister(p, auth);
                    return 1;
                }))
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runGated(ctx.getSource(), auth, p -> doLogin(p, auth,
                                StringArgumentType.getString(ctx, "password"))))));

        root.then(Commands.literal("register")
                .executes(ctx -> runGated(ctx.getSource(), auth, p -> {
                    AuthGui.openLoginOrRegister(p, auth);
                    return 1;
                }))
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runGated(ctx.getSource(), auth, p -> doRegister(p, auth,
                                StringArgumentType.getString(ctx, "password"))))));

        // 裸 /yzts logout 发聊天按钮让玩家二选一；带参数（quit/stay/throttle）直接执行
        root.then(Commands.literal("logout")
                .executes(ctx -> runGated(ctx.getSource(), auth, p -> { LogoutPrompt.send(p); return 1; }))
                .then(Commands.literal("quit").executes(ctx -> runGated(ctx.getSource(), auth,
                        p -> { auth.logout(p, "disconnect"); return 1; })))
                .then(Commands.literal("stay").executes(ctx -> runGated(ctx.getSource(), auth,
                        p -> { auth.logout(p, "lock"); return 1; })))
                .then(Commands.literal("throttle").executes(ctx -> runGated(ctx.getSource(), auth,
                        p -> { auth.logout(p, "disconnect-throttle"); return 1; }))));

        // 账户管理组：/yzts account [email <地址> | changepassword <旧> <新>]
        LiteralArgumentBuilder<CommandSourceStack> account = Commands.literal("account")
                .executes(ctx -> runGated(ctx.getSource(), auth, p -> { AccountPanel.open(p, auth); return 1; }));

        account.then(Commands.literal("email")
                .then(Commands.argument("address", StringArgumentType.greedyString())
                        .executes(ctx -> runGated(ctx.getSource(), auth, p ->
                                { EmailFlow.apply(p, auth, StringArgumentType.getString(ctx, "address")); return 1; }))));

        account.then(Commands.literal("changepassword")
                .then(Commands.argument("old", StringArgumentType.greedyString())
                        .then(Commands.argument("new", StringArgumentType.greedyString())
                                .executes(ctx -> runGated(ctx.getSource(), auth, p -> {
                                    boolean ok = auth.changePassword(p, StringArgumentType.getString(ctx, "old"),
                                            StringArgumentType.getString(ctx, "new"));
                                    p.sendSystemMessage(Lang.msg(ok ? "changepw_ok" : "changepw_bad"));
                                    if (ok) com.yztc.auth.ui.Sounds.ok(p);
                                    else com.yztc.auth.ui.Sounds.fail(p);
                                    return 1;
                                })))));

        root.then(account);

        root.then(Commands.literal("help").executes(ctx -> runPlayer(ctx.getSource(), p -> {
            com.yztc.auth.ui.BookGuide.give(p);
            return 1;
        })));

        // 双入口二选一的「铁砧输入」按钮：按令牌打开已注册的铁砧输入框
        root.then(Commands.literal("input")
                .then(Commands.argument("token", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            com.yztc.auth.ui.InputPrompt.fire(
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "token"));
                            return 1;
                        })));

        // 玩家自助命令在根下；管理员专属统一在 /yzts admin 组内（AdminCommands 内再挂 status/risk）
        AdminCommands.attach(root, auth, gate);
        LocationCommand.attach(root, auth);
        MailCommand.attach(root, auth);
        com.yztc.auth.broadcast.BroadcastCommand.attach(root, auth);
        com.yztc.auth.title.TitleCommand.attach(root, auth);

        d.register(root);
    }

    private interface PlayerAction { int run(ServerPlayer p) throws Exception; }

    /** 未过闸者只能执行 /yzts gate 与 /yzts help。返回 true 表示已拒绝并已提示。 */
    public static boolean gateBlocked(ServerPlayer p, AuthManager auth) {
        if (!auth.gateRequired(p)) return false;
        p.sendSystemMessage(Lang.msg("gate_required"));
        WelcomeSync.promptGate(p);
        return true;
    }

    /** 登录/注册/登出/改密/邮箱等：先过闸门检查，再执行。 */
    private static int runGated(CommandSourceStack src, AuthManager auth, PlayerAction action) {
        return runPlayer(src, p -> gateBlocked(p, auth) ? 1 : action.run(p));
    }

    private static int runPlayer(CommandSourceStack src, PlayerAction action) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            return action.run(p);
        } catch (Exception e) {
            src.sendFailure(net.minecraft.network.chat.Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    /** 闸门验密：成功即提示并自动接上登录/注册铁砧框。命令与铁砧提交共用此逻辑。 */
    public static int doGate(ServerPlayer p, AuthManager auth, String pw) {
        GateManager.GateResult result = attemptGate(p, auth, pw);
        if (result == GateManager.GateResult.OK) {
            WelcomeSync.promptLogin(p, auth);
            AuthGui.openLoginOrRegister(p, auth);
        }
        return 1;
    }

    /** 执行闸门校验并提示结果；GUI 调用方据此决定保留或切换输入窗口。 */
    public static GateManager.GateResult attemptGate(ServerPlayer p, AuthManager auth, String pw) {
        GateManager.GateResult result = auth.gateAttempt(p, pw);
        switch (result) {
            case NOT_SET -> p.sendSystemMessage(Lang.msg("gate_not_set"));
            case OK -> {
                p.sendSystemMessage(Lang.msg("gate_ok"));
                com.yztc.auth.ui.Sounds.ok(p);
            }
            case BAD -> {
                p.sendSystemMessage(Lang.msg("gate_bad"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
            case RATE_LIMITED -> {
                p.sendSystemMessage(Lang.msg("login_rate_limited"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
        }
        return result;
    }

    public static int doLogin(ServerPlayer p, AuthManager auth, String pw) {
        if (auth.isAuthenticated(p)) { p.sendSystemMessage(Lang.msg("already_logged_in")); return 1; }
        switch (auth.login(p, pw)) {
            case OK -> {
                p.sendSystemMessage(Lang.msg("login_ok", p.getGameProfile().getName()));
                com.yztc.auth.ui.Sounds.ok(p);
            }
            case BAD -> {
                // A2：失败后给剩余次数；若本次已达阈值触发锁定，直接给锁定提示
                Account a = auth.accountOf(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
                if (a != null && a.lockedUntil > System.currentTimeMillis()) {
                    long secs = Math.max(0, (a.lockedUntil - System.currentTimeMillis()) / 1000);
                    p.sendSystemMessage(Lang.msg("login_locked", secs));
                } else {
                    int remaining = AuthManager.remainingAttempts(
                            a == null ? 0 : a.failedAttempts, AuthConfig.maxLoginAttempts());
                    p.sendSystemMessage(Lang.msg("login_bad_remaining", String.valueOf(remaining)));
                }
                com.yztc.auth.ui.Sounds.fail(p);
            }
            case NO_ACCOUNT -> {
                p.sendSystemMessage(Lang.msg("not_registered"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
            case LOCKED -> {
                Account a = auth.accountOf(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
                long secs = a == null ? 0 : Math.max(0, (a.lockedUntil - System.currentTimeMillis()) / 1000);
                p.sendSystemMessage(Lang.msg("login_locked", secs));
                com.yztc.auth.ui.Sounds.fail(p);
            }
            case RATE_LIMITED -> {
                p.sendSystemMessage(Lang.msg("login_rate_limited"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
        }
        return 1;
    }

    public static int doRegister(ServerPlayer p, AuthManager auth, String pw) {
        if (auth.isAuthenticated(p)) { p.sendSystemMessage(Lang.msg("already_logged_in")); return 1; }
        if (pw.length() < AuthConfig.minPasswordLength()) {
            p.sendSystemMessage(Lang.msg("register_too_short", AuthConfig.minPasswordLength()));
            com.yztc.auth.ui.Sounds.fail(p);
            return 1;
        }
        switch (auth.register(p, pw)) {
            case OK -> {
                p.sendSystemMessage(Lang.msg("register_ok"));
                // A3：提示密码强度（弱/中/强），引导玩家用更强的密码
                p.sendSystemMessage(Lang.msg("password_strength", Lang.msg(strengthKey(pw))));
                com.yztc.auth.ui.Sounds.ok(p);
            }
            case NO_ACCOUNT -> {
                p.sendSystemMessage(Lang.msg("register_exists"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
            case RATE_LIMITED -> {
                p.sendSystemMessage(Lang.msg("login_rate_limited"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
            default -> {
                p.sendSystemMessage(Lang.msg("not_registered"));
                com.yztc.auth.ui.Sounds.fail(p);
            }
        }
        return 1;
    }

    private static String strengthKey(String pw) {
        return switch (com.yztc.auth.data.PasswordStrength.of(pw)) {
            case WEAK -> "strength_weak";
            case MEDIUM -> "strength_mid";
            case STRONG -> "strength_strong";
        };
    }
}
