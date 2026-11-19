package com.yztc.auth.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.net.InetAddress;

/**
 * Allows a trusted server-side integration mod to provide a verified client address.
 *
 * <p>Listeners run synchronously on the server thread. The first listener to provide a
 * non-null address wins; listeners must validate the proxy/forwarding source before setting it.
 * If no listener sets an address, the caller uses the player's connection address.
 */
public final class ClientIpResolveEvent extends Event {
    private final ServerPlayer player;
    private InetAddress clientAddress;

    public ClientIpResolveEvent(ServerPlayer player) {
        this.player = player;
    }

    public static InetAddress resolve(ServerPlayer player) {
        ClientIpResolveEvent event = new ClientIpResolveEvent(player);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        return event.getClientAddress();
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public InetAddress getClientAddress() {
        return clientAddress;
    }

    /** Set a verified client address. A later listener cannot replace the first value. */
    public boolean setClientAddress(InetAddress address) {
        if (address == null || clientAddress != null) return false;
        clientAddress = address;
        return true;
    }
}
