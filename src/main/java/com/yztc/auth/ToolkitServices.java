package com.yztc.auth;

import com.yztc.auth.audit.AuditLog;
import com.yztc.auth.auth.AuthHolder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.AdminCommands;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.MailStore;
import com.yztc.auth.data.StoreHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class ToolkitServices {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    private ToolkitServices() {}

    public static AuthManager initialize(Path dataDirectory, Path gameDirectory) {
        AccountStore store = new AccountStore(
                dataDirectory.resolve("users.json"));
        try {
            store.load();
        } catch (IOException e) {
            LOGGER.error("[yzt_super] 加载 users.json 失败，暂停写入该库，请修复文件后重启", e);
        }
        StoreHolder.set(store);

        // 这里只建对象、不 load：MailStore.load 会读 SERVER 配置（超期天数），
        // 而 SERVER 配置要等世界加载后才可用，过早调用会取不到值。真正的 load 放在 ServerStarted。
        StoreHolder.setMail(new MailStore(dataDirectory.resolve("mail.json")));
        StoreHolder.setLastSeen(new LastSeenStore(dataDirectory.resolve("lastseen.json")));
        StoreHolder.setRisk(new com.yztc.auth.data.RiskStore(dataDirectory.resolve("risk.json")));
        StoreHolder.setAudit(new AuditLog(gameDirectory.resolve("logs").resolve("yzt_super-audit.log")));
        StoreHolder.setTitles(new com.yztc.auth.title.TitleStore(
                dataDirectory.resolve("titles.json")));

        GateManager gate = new GateManager(store);
        AuthManager auth = new AuthManager(store, gate);
        AuthHolder.AUTH = auth;
        AdminCommands.setGate(gate);
        return auth;
    }
}
