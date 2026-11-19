package com.yztc.auth.lockdown;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

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
        if (auth) {
            p.gameMode.getGameModeForPlayer().updatePlayerAbilities(caps);
        } else {
            caps.mayBuild = false;
            caps.instabuild = false;
            caps.invulnerable = true;
        }
        p.onUpdateAbilities();
        p.connection.send(new ClientboundSetCarriedItemPacket(p.getInventory().selected));
        p.inventoryMenu.sendAllDataToRemote();
    }
}
