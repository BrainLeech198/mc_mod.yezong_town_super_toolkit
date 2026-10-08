package com.yztc.auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.ui.WelcomeSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class RootCommand {
    private RootCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, AuthManager auth, GateManager gate) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("yztauth");

        root.then(Commands.literal("gate")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runPlayer(ctx.getSource(), p -> {
                            String pw = StringArgumentType.getString(ctx, "password");
                            switch (gate.verify(p, pw)) {
                                case NOT_SET -> p.sendSystemMessage(Lang.msg("gate_not_set"));
                                case OK -> {
                                    p.sendSystemMessage(Lang.msg("gate_ok"));
                                    WelcomeSync.promptLogin(p, auth);
                                }
                                case BAD -> p.sendSystemMessage(Lang.msg("gate_bad"));
                            }
                            return 1;
                        }))));

        root.then(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runPlayer(ctx.getSource(), p -> doLogin(p, auth,
                                StringArgumentType.getString(ctx, "password"))))));

        root.then(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> runPlayer(ctx.getSource(), p -> doRegister(p, auth,
                                StringArgumentType.getString(ctx, "password"))))));

        root.then(Commands.literal("logout")
                .executes(ctx -> runPlayer(ctx.getSource(), p -> { auth.logout(p, AuthConfig.logoutMode()); return 1; }))
                .then(Commands.literal("disconnect").executes(ctx -> runPlayer(ctx.getSource(), p -> { auth.logout(p, "disconnect"); return 1; })))
                .then(Commands.literal("lock").executes(ctx -> runPlayer(ctx.getSource(), p -> { auth.logout(p, "lock"); return 1; })))
                .then(Commands.literal("disconnect-throttle").executes(ctx -> runPlayer(ctx.getSource(), p -> { auth.logout(p, "disconnect-throttle"); return 1; }))));

        root.then(Commands.literal("changepassword")
                .then(Commands.argument("old", StringArgumentType.greedyString())
                        .then(Commands.argument("new", StringArgumentType.greedyString())
                                .executes(ctx -> runPlayer(ctx.getSource(), p -> {
                                    boolean ok = auth.changePassword(p, StringArgumentType.getString(ctx, "old"),
                                            StringArgumentType.getString(ctx, "new"));
                                    p.sendSystemMessage(Lang.msg(ok ? "changepw_ok" : "changepw_bad"));
                                    return 1;
                                })))));

        root.then(Commands.literal("email")
                .then(Commands.argument("address", StringArgumentType.greedyString())
                        .executes(ctx -> runPlayer(ctx.getSource(), p -> {
                            String addr = StringArgumentType.getString(ctx, "address");
                            String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
                            Account a = auth.accountOf(name);
                            if (a != null) { a.email = addr; auth.persistNow(); }
                            p.sendSystemMessage(Lang.msg("email_set"));
                            return 1;
                        }))));

        root.then(Commands.literal("help").executes(ctx -> runPlayer(ctx.getSource(), p -> {
            com.yztc.auth.ui.BookGuide.give(p);
            return 1;
        })));

        // 管理/控制台子指令在 Task 14 追加
        AdminCommands.attach(root, auth, gate);

        d.register(root);
    }

    private interface PlayerAction { int run(ServerPlayer p) throws Exception; }

    private static int runPlayer(CommandSourceStack src, PlayerAction action) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            return action.run(p);
        } catch (Exception e) {
            src.sendFailure(net.minecraft.network.chat.Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    public static int doLogin(ServerPlayer p, AuthManager auth, String pw) {
        if (auth.isAuthenticated(p)) { p.sendSystemMessage(Lang.msg("already_logged_in")); return 1; }
        switch (auth.login(p, pw)) {
            case OK -> p.sendSystemMessage(Lang.msg("login_ok", p.getGameProfile().getName()));
            case BAD -> p.sendSystemMessage(Lang.msg("login_bad"));
            case NO_ACCOUNT -> p.sendSystemMessage(Lang.msg("not_registered"));
            case LOCKED -> {
                Account a = auth.accountOf(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
                long secs = a == null ? 0 : Math.max(0, (a.lockedUntil - System.currentTimeMillis()) / 1000);
                p.sendSystemMessage(Lang.msg("login_locked", secs));
            }
            case RATE_LIMITED -> p.sendSystemMessage(Lang.msg("login_rate_limited"));
        }
        return 1;
    }

    public static int doRegister(ServerPlayer p, AuthManager auth, String pw) {
        if (auth.isAuthenticated(p)) { p.sendSystemMessage(Lang.msg("already_logged_in")); return 1; }
        if (pw.length() < AuthConfig.minPasswordLength()) {
            p.sendSystemMessage(Lang.msg("register_too_short", AuthConfig.minPasswordLength()));
            return 1;
        }
        switch (auth.register(p, pw)) {
            case OK -> p.sendSystemMessage(Lang.msg("register_ok"));
            case NO_ACCOUNT -> p.sendSystemMessage(Lang.msg("register_exists"));
            case RATE_LIMITED -> p.sendSystemMessage(Lang.msg("login_rate_limited"));
            default -> p.sendSystemMessage(Lang.msg("not_registered"));
        }
        return 1;
    }
}
