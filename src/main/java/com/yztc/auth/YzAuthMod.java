package com.yztc.auth;

import com.yztc.auth.auth.AuthEvents;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.lockdown.LockdownHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;


@Mod(YzAuthMod.MODID)
public class YzAuthMod {
    public static final String MODID = "yzt_super";

    // 47.4.0 里 registerConfig 本身未废弃，被标记 forRemoval 的只有静态 get()；
    // 唯一的构造器注入类型 FMLJavaModLoadingContext 同样是 forRemoval，故无更干净的写法。
    @SuppressWarnings("removal")
    public YzAuthMod() {
        // SERVER 配置由 Forge 落在每个世界的 serverconfig/yzt_super-server.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AuthConfig.SPEC);

        AuthManager auth = ToolkitServices.initialize(FMLPaths.CONFIGDIR.get().resolve(MODID), FMLPaths.GAMEDIR.get());
        LockdownHandler.bind(auth);

        MinecraftForge.EVENT_BUS.register(AuthEvents.class);
        MinecraftForge.EVENT_BUS.register(LockdownHandler.class);
        MinecraftForge.EVENT_BUS.register(com.yztc.auth.title.TitleEvents.class);
    }
}
