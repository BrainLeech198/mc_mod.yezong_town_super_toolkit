package com.yztc.auth.auth;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lockdown.LockdownSync;
import com.yztc.auth.lang.ServerLang;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.ui.Banner;
import com.yztc.auth.ui.ModInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** 服务端事件入口：进服锁定、退服清理、指令注册、开服加载。 */
public final class PlayerLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    /** 每进程只给首个进服管理员发一次启动横幅。 */
    private static boolean bannerSentInGame = false;

    private PlayerLifecycle() {}

    public static void started() {
        AuthHolder.AUTH.load();
        // SERVER 配置此时已加载，可安全读取（MailStore.load 会用到 mailExpireDays）
        MailStore mail = StoreHolder.mail();
        if (mail != null) {
            try {
                mail.load();
            } catch (IOException ex) {
                LOGGER.error("[yzt_super] 加载 mail.json 失败，暂停写入该库，请修复文件后重启", ex);
            }
        }
        LastSeenStore lastSeen = StoreHolder.lastSeen();
        if (lastSeen != null) {
            try {
                lastSeen.load();
            } catch (IOException ex) {
                LOGGER.error("[yzt_super] 加载 lastseen.json 失败，暂停写入该库，请修复文件后重启", ex);
            }
        }
        com.yztc.auth.data.RiskStore risk = StoreHolder.risk();
        if (risk != null) {
            try {
                risk.load();
            } catch (IOException ex) {
                LOGGER.error("[yzt_super] 加载 risk.json 失败，暂停写入该库，请修复文件后重启", ex);
            }
        }
        com.yztc.auth.title.TitleStore titles = StoreHolder.titles();
        if (titles != null) {
            try {
                titles.load();
            } catch (IOException ex) {
                LOGGER.error("[yzt_super] 加载 titles.json 失败，暂停写入该库，请修复文件后重启", ex);
            }
        }
        printStartupBanner();
    }

    /** 开服时往控制台打印启动横幅（ANSI 上色）。受 startupBanner 开关控制。 */
    private static void printStartupBanner() {
        if (!AuthConfig.startupBanner()) return;
        try {
            for (String line : Banner.consoleLines(
                    AuthConfig.serverName(), ModInfo.name(), ModInfo.version(), ModInfo.author(),
                    ModInfo.minecraft(), ModInfo.loaderVersion(), labels())) {
                System.out.println(line);
            }
        } catch (Throwable t) {
            LOGGER.warn("[yzt_super] 打印启动横幅失败：{}", t.toString());
        }
    }

    /** 横幅标签文案：从语言表按服务端兜底语言取（本类不再内置中文）。 */
    private static Banner.Labels labels() {
        return new Banner.Labels(
                ServerLang.template("banner_mod_tag"),
                ServerLang.template("banner_version"),
                ServerLang.template("banner_minecraft"),
                ModInfo.loaderLabel(),
                ServerLang.template("banner_author"),
                ServerLang.template("banner_status"));
    }

    public static void joined(ServerPlayer p) {
        AuthHolder.AUTH.onJoin(p);
        if (!AuthHolder.AUTH.closingSession(p)) {
            LockdownSync.lock(p);
            maybeSendBannerInGame(p);
        }
    }

    /** 首个进服的管理员收到一次游戏内横幅；普通玩家不发，且每进程只发一次。 */
    private static void maybeSendBannerInGame(ServerPlayer p) {
        if (bannerSentInGame || !AuthConfig.startupBanner()) return;
        if (!Permissions.isAdmin(p)) return;
        bannerSentInGame = true;
        try {
            for (Component c : Banner.chatLines(
                    AuthConfig.serverName(), ModInfo.name(), ModInfo.version(), ModInfo.author(),
                    ModInfo.minecraft(), ModInfo.loaderVersion(), labels())) {
                p.sendSystemMessage(c);
            }
        } catch (Throwable t) {
            LOGGER.warn("[yzt_super] 发送游戏内横幅失败：{}", t.toString());
        }
    }

    public static void left(ServerPlayer p) {
        ServerPlayer current = p.server.getPlayerList().getPlayer(p.getUUID());
        if (current != null && current != p) return;
        recordLastSeen(p);
        AuthHolder.AUTH.onLeave(p);
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
        s.gameMode = p.gameMode.getGameModeForPlayer().getName();
        try {
            store.putAndSave(s);
        } catch (IOException ex) {
            LOGGER.warn("[yzt_super] lastseen.json 写入失败：{}", ex.toString());
        }
    }
}
