package com.yztc.auth.auth;

import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lockdown.LockdownSync;
import com.yztc.auth.ui.UiSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** 服务端事件入口：进服锁定、退服清理、指令注册、开服加载。 */
public final class AuthEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("yztauth");

    private AuthEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        AuthHolder.AUTH.load();
        // SERVER 配置此时已加载，可安全读取（MailStore.load 会用到 mailExpireDays）
        MailStore mail = StoreHolder.mail();
        if (mail != null) {
            try {
                mail.load();
            } catch (IOException ex) {
                LOGGER.error("[yztauth] 加载 mail.json 失败，使用空库（旧文件保留）", ex);
            }
        }
        LastSeenStore lastSeen = StoreHolder.lastSeen();
        if (lastSeen != null) {
            try {
                lastSeen.load();
            } catch (IOException ex) {
                LOGGER.error("[yztauth] 加载 lastseen.json 失败，使用空库（旧文件保留）", ex);
            }
        }
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
            recordLastSeen(p);
            UiSession.forget(p.getUUID());
            AuthHolder.AUTH.onLeave(p);
        }
    }

    /** 退服时记录「最后下线位置」快照（一人一条，覆盖旧值）。 */
    private static void recordLastSeen(ServerPlayer p) {
        LastSeenStore store = StoreHolder.lastSeen();
        if (store == null) return;
        LastSeen s = new LastSeen();
        s.name = p.getGameProfile().getName();
        s.dimension = p.level().dimension().location().toString();
        s.x = p.getX(); s.y = p.getY(); s.z = p.getZ();
        s.yaw = p.getYRot(); s.pitch = p.getXRot();
        s.at = System.currentTimeMillis();
        store.put(s);
        try {
            store.save();
        } catch (IOException ex) {
            LOGGER.warn("[yztauth] lastseen.json 写入失败：{}", ex.toString());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        com.yztc.auth.command.RootCommand.register(e.getDispatcher(),
                AuthHolder.AUTH, com.yztc.auth.command.AdminCommands.gate());
        com.yztc.auth.command.BareCommands.register(e.getDispatcher(), AuthHolder.AUTH);
    }
}
