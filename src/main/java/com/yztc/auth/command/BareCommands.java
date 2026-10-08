package com.yztc.auth.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yztc.auth.auth.AuthManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class BareCommands {
    private BareCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d, AuthManager auth) {
        d.register(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return RootCommand.doLogin(p, auth, StringArgumentType.getString(ctx, "password"));
                        })));
        d.register(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return RootCommand.doRegister(p, auth, StringArgumentType.getString(ctx, "password"));
                        })));
    }
}
