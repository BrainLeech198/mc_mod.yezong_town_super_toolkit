package com.yztc.auth.auth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Forge 1.20.1 事件到共享服务端生命周期的映射。 */
public final class AuthEvents {
    private AuthEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        PlayerLifecycle.started();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerLifecycle.joined(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) PlayerLifecycle.left(p);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        com.yztc.auth.command.RootCommand.register(e.getDispatcher(),
                AuthHolder.AUTH, com.yztc.auth.command.AdminCommands.gate());
        com.yztc.auth.command.BareCommands.register(e.getDispatcher(), AuthHolder.AUTH);
    }
}
