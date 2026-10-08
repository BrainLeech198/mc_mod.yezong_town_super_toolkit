package com.yztc.auth.lockdown;

import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;

public final class LockdownSync {
    private LockdownSync() {}

    public static void lock(ServerPlayer p) {
        apply(p, false);
    }

    public static void authenticate(ServerPlayer p) {
        apply(p, true);
    }

    private static void apply(ServerPlayer p, boolean auth) {
        Abilities caps = p.getAbilities();
        caps.mayBuild = auth;
        caps.instabuild = false;
        caps.invulnerable = !auth;
        p.onUpdateAbilities();
        p.connection.send(new ClientboundPlayerAbilitiesPacket(caps));
        p.connection.send(new ClientboundSetCarriedItemPacket(p.getInventory().selected));
        p.connection.send(new ClientboundContainerSetContentPacket(
                p.inventoryMenu.containerId,
                p.inventoryMenu.incrementStateId(),
                p.getInventory().items,
                ItemStack.EMPTY));
    }
}
