package com.yztc.auth.data;

import com.yztc.auth.audit.AuditLog;

/** 装配期绑定、供事件与指令共享的各项存储单例。 */
public final class StoreHolder {
    private static AccountStore instance;
    private static MailStore mail;
    private static LastSeenStore lastSeen;
    private static AuditLog audit;

    private StoreHolder() {}

    public static void set(AccountStore s) { instance = s; }
    public static AccountStore get() { return instance; }

    public static void setMail(MailStore m) { mail = m; }
    public static MailStore mail() { return mail; }

    public static void setLastSeen(LastSeenStore l) { lastSeen = l; }
    public static LastSeenStore lastSeen() { return lastSeen; }

    public static void setAudit(AuditLog a) { audit = a; }
    public static AuditLog audit() { return audit; }
}
