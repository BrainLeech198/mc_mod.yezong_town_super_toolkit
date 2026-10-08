package com.yztc.auth;

import com.yztc.auth.auth.AuthEvents;
import com.yztc.auth.auth.AuthHolder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.audit.AuditLog;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lockdown.LockdownHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Mod(YzAuthMod.MODID)
public class YzAuthMod {
    public static final String MODID = "yztauth";
    private static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    // 47.4.0 里 registerConfig 本身未废弃，被标记 forRemoval 的只有静态 get()；
    // 唯一的构造器注入类型 FMLJavaModLoadingContext 同样是 forRemoval，故无更干净的写法。
    @SuppressWarnings("removal")
    public YzAuthMod() {
        // SERVER 配置由 Forge 落在每个世界的 serverconfig/yztauth-server.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AuthConfig.SPEC);

        AccountStore store = new AccountStore(
                FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("users.json"));
        try {
            store.load();
        } catch (IOException e) {
            LOGGER.error("[yztauth] 加载 users.json 失败，使用空库（旧文件保留）", e);
        }
        StoreHolder.set(store);

        MailStore mail = new MailStore(FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("mail.json"));
        try {
            mail.load();
        } catch (IOException e) {
            LOGGER.error("[yztauth] 加载 mail.json 失败，使用空库（旧文件保留）", e);
        }
        StoreHolder.setMail(mail);

        LastSeenStore lastSeen = new LastSeenStore(FMLPaths.CONFIGDIR.get().resolve(MODID).resolve("lastseen.json"));
        try {
            lastSeen.load();
        } catch (IOException e) {
            LOGGER.error("[yztauth] 加载 lastseen.json 失败，使用空库（旧文件保留）", e);
        }
        StoreHolder.setLastSeen(lastSeen);

        StoreHolder.setAudit(new AuditLog(FMLPaths.GAMEDIR.get().resolve("logs").resolve("yztauth-audit.log")));

        GateManager gate = new GateManager(store);
        AuthManager auth = new AuthManager(store, gate);
        AuthHolder.AUTH = auth;
        AdminCommands.setGate(gate);

        LockdownHandler.bind(auth);

        MinecraftForge.EVENT_BUS.register(AuthEvents.class);
        MinecraftForge.EVENT_BUS.register(LockdownHandler.class);
    }
}
