package com.yztc.auth.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

import java.net.InetAddress;

/** 可信代理适配器在服务端注册。返回已验证的地址，或 null 以继续尝试其他提供方。 */
@FunctionalInterface
public interface ClientIpResolveEvent {
    Event<ClientIpResolveEvent> EVENT = EventFactory.createArrayBacked(ClientIpResolveEvent.class,
            listeners -> player -> {
                for (ClientIpResolveEvent listener : listeners) {
                    InetAddress address = listener.clientAddress(player);
                    if (address != null) return address;
                }
                return null;
            });

    InetAddress clientAddress(ServerPlayer player);

    static InetAddress resolve(ServerPlayer player) {
        return EVENT.invoker().clientAddress(player);
    }
}
