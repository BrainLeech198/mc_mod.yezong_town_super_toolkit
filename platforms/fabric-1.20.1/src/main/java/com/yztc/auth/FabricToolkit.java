package com.yztc.auth;

import com.yztc.auth.config.FabricWorldConfig;
import com.yztc.auth.fabric.FabricEvents;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricToolkit implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FabricWorldConfig.register();
        FabricLoader loader = FabricLoader.getInstance();
        ToolkitServices.initialize(loader.getConfigDir().resolve("yzt_super"), loader.getGameDir());
        FabricEvents.register();
    }
}
