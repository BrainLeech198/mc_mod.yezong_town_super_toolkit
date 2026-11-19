package com.yztc.auth.fabric;

import com.yztc.auth.auth.AuthHolder;
import com.yztc.auth.auth.PlayerLifecycle;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.command.BareCommands;
import com.yztc.auth.command.RootCommand;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lockdown.LockdownRules;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class FabricEvents {
    private FabricEvents() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            RootCommand.register(dispatcher, AuthHolder.AUTH, AdminCommands.gate());
            BareCommands.register(dispatcher, AuthHolder.AUTH);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> PlayerLifecycle.started());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FabricTitles.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LockdownRules.tick(player);
                if (locked(player)) FabricTitles.removeNameplate(player);
                else if (server.getTickCount() % 20 == 0) FabricTitles.syncNameplate(player);
            }
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> interaction(player));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> interaction(player));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, face) -> interaction(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> interaction(player));
        UseItemCallback.EVENT.register((player, level, hand) -> interaction(player) == InteractionResult.FAIL
                ? InteractionResultHolder.fail(player.getItemInHand(hand))
                : InteractionResultHolder.pass(player.getItemInHand(hand)));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !locked(player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !locked(entity));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, parameters) -> allowAction(sender));
    }

    // 连接与退出在 PlayerList 的 vanilla 边界接入，见 PlayerListMixin。
    public static boolean locked(Entity entity) {
        return entity instanceof ServerPlayer player
                && (AuthHolder.AUTH == null || !AuthHolder.AUTH.isAuthenticated(player));
    }

    public static boolean allowAction(Player player) {
        if (!locked(player)) return true;
        player.sendSystemMessage(Lang.msg("need_login"));
        return false;
    }

    private static InteractionResult interaction(Player player) {
        return allowAction(player) ? InteractionResult.PASS : InteractionResult.FAIL;
    }
}
