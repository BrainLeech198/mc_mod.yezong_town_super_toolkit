package com.yztc.auth.auth;

import com.yztc.auth.lockdown.LockdownSync;
import com.yztc.auth.ui.UiSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** 服务端事件入口：进服锁定、退服清理、指令注册、开服加载。 */
public final class AuthEvents {
    private AuthEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        AuthHolder.AUTH.load();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            AuthHolder.AUTH.onJoin(p);
            LockdownSync.lock(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            UiSession.forget(p.getUUID());
            AuthHolder.AUTH.onLeave(p);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        com.yztc.auth.command.RootCommand.register(e.getDispatcher(),
                AuthHolder.AUTH, com.yztc.auth.command.AdminCommands.gate());
        com.yztc.auth.command.BareCommands.register(e.getDispatcher(), AuthHolder.AUTH);
    }
}
