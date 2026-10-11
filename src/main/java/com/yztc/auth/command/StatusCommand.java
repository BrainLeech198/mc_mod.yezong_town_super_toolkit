package com.yztc.auth.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** `/yzts admin status`：在线人数与未登录名单。 */
public final class StatusCommand {
    private StatusCommand() {}

    /** 状态文本：在线人数 + 未登录名单（空名单显示「-」）。 */
    public static Component render(String[] pendingNames, int online) {
        int n = pendingNames == null ? 0 : pendingNames.length;
        String names = n == 0 ? "-" : String.join(", ", pendingNames);
        return Lang.msg("status_line", String.valueOf(online), String.valueOf(n), names);
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("status")
                .requires(s -> !s.isPlayer() || Permissions.isAdmin(s.getPlayer()))
                .executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    MinecraftServer server = src.getServer();
                    List<ServerPlayer> players = server.getPlayerList().getPlayers();
                    List<String> pending = new ArrayList<>();
                    for (ServerPlayer p : players) {
                        if (auth.isPending(p)) pending.add(p.getGameProfile().getName());
                    }
                    int online = players.size();
                    src.sendSuccess(() -> render(pending.toArray(new String[0]), online), false);
                    return 1;
                }));
    }
}
