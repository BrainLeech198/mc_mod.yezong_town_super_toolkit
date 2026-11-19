package com.yztc.auth.title;

import com.yztc.auth.fabric.FabricTitles;
import net.minecraft.server.level.ServerPlayer;

public final class TitleEvents {
    private TitleEvents() {}

    public static void refreshDisplay(ServerPlayer player) {
        FabricTitles.refreshDisplay(player);
    }
}
