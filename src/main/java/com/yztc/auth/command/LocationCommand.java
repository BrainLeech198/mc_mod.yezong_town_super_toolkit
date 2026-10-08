package com.yztc.auth.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.perm.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** `/yztauth where <玩家>` 与 `/yztauth tp <玩家>`：查询/传送到下线位置。 */
public final class LocationCommand {
    private static final String UNKNOWN = "?";

    private LocationCommand() {}

    /** 纯逻辑：仅管理玩家（非控制台）可 tp。 */
    public static boolean canTp(boolean executorIsPlayer, boolean executorIsAdmin) {
        return executorIsPlayer && executorIsAdmin;
    }

    /** 位置文本：有记录给坐标，无记录给 where_none。 */
    public static Component whereText(LastSeen s) {
        if (s == null) return Lang.msg("where_none", UNKNOWN);
        return Lang.msg("where_result", s.name, s.dimension, fmt(s.x), fmt(s.y), fmt(s.z));
    }

    private static String fmt(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root, AuthManager auth) {
        root.then(Commands.literal("where")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            LastSeen s = lookup(StringArgumentType.getString(ctx, "player"));
                            ctx.getSource().sendSuccess(() -> whereText(s), false);
                            return 1;
                        })));

        root.then(Commands.literal("tp")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            ServerPlayer exec = src.getPlayer();
                            boolean isPlayer = exec != null;
                            boolean isAdmin = isPlayer && Permissions.isAdmin(exec);
                            if (!canTp(isPlayer, isAdmin)) {
                                src.sendFailure(isPlayer ? Lang.msg("no_permission") : Lang.msg("tp_console"));
                                return 0;
                            }
                            LastSeen s = lookup(StringArgumentType.getString(ctx, "player"));
                            if (s == null) { src.sendFailure(whereText(null)); return 0; }
                            ServerLevel level = resolveLevel(src.getServer(), s.dimension);
                            if (level == null) { src.sendFailure(whereText(null)); return 0; }
                            exec.teleportTo(level, s.x, s.y, s.z, s.yaw, s.pitch);
                            src.sendSuccess(() -> Lang.msg("tp_result", s.name), false);
                            return 1;
                        })));
    }

    private static LastSeen lookup(String name) {
        LastSeenStore store = StoreHolder.lastSeen();
        return store == null ? null : store.get(name.toLowerCase(Locale.ROOT));
    }

    /** 维度解析失败返回 null，由调用方回退提示，不崩溃。 */
    private static ServerLevel resolveLevel(MinecraftServer server, String dim) {
        if (server == null || dim == null) return null;
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dim)) return lvl;
        }
        return null;
    }
}
