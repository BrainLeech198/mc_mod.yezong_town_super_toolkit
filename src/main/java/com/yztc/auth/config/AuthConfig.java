package com.yztc.auth.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class AuthConfig {
    public static final ForgeConfigSpec SPEC;
    private static final Values V;

    static {
        Pair<Values, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Values::new);
        V = pair.getLeft();
        SPEC = pair.getRight();
    }

    private AuthConfig() {}

    public static int minPasswordLength()          { return V.minPasswordLength.get(); }
    public static int maxLoginAttempts()           { return V.maxLoginAttempts.get(); }
    public static int lockoutSeconds()             { return V.lockoutSeconds.get(); }
    public static int ipRateLimitCount()           { return V.ipRateLimitCount.get(); }
    public static int ipRateLimitWindowSeconds()   { return V.ipRateLimitWindowSeconds.get(); }
    public static int loginTimeoutSeconds()        { return V.loginTimeoutSeconds.get(); }
    public static String ipResolution()            { return V.ipResolution.get(); }
    public static String forwardedHeaderName()     { return V.forwardedHeaderName.get(); }
    public static String logoutMode()              { return V.logoutMode.get(); }
    public static int logoutThrottleSeconds()      { return V.logoutThrottleSeconds.get(); }
    public static int pbkdf2Iterations()           { return V.pbkdf2Iterations.get(); }
    public static String legacyUsersFile()         { return V.legacyUsersFile.get(); }
    public static int mailMaxPerPlayer()           { return V.mailMaxPerPlayer.get(); }
    public static int mailExpireDays()             { return V.mailExpireDays.get(); }
    public static boolean auditLogEnabled()        { return V.auditLogEnabled.get(); }
    public static boolean notifyAdminOnLogin()     { return V.notifyAdminOnLogin.get(); }
    public static boolean afkLogoutEnabled()       { return V.afkLogoutEnabled.get(); }
    public static int afkLogoutSeconds()           { return V.afkLogoutSeconds.get(); }
    public static String welcomeTitleText()        { return V.welcomeTitleText.get(); }
    public static String welcomeSubText()          { return V.welcomeSubText.get(); }

    private static final class Values {
        final ForgeConfigSpec.IntValue minPasswordLength;
        final ForgeConfigSpec.IntValue maxLoginAttempts;
        final ForgeConfigSpec.IntValue lockoutSeconds;
        final ForgeConfigSpec.IntValue ipRateLimitCount;
        final ForgeConfigSpec.IntValue ipRateLimitWindowSeconds;
        final ForgeConfigSpec.IntValue loginTimeoutSeconds;
        final ForgeConfigSpec.ConfigValue<String> ipResolution;
        final ForgeConfigSpec.ConfigValue<String> forwardedHeaderName;
        final ForgeConfigSpec.ConfigValue<String> logoutMode;
        final ForgeConfigSpec.IntValue logoutThrottleSeconds;
        final ForgeConfigSpec.IntValue pbkdf2Iterations;
        final ForgeConfigSpec.ConfigValue<String> legacyUsersFile;
        final ForgeConfigSpec.IntValue mailMaxPerPlayer;
        final ForgeConfigSpec.IntValue mailExpireDays;
        final ForgeConfigSpec.BooleanValue auditLogEnabled;
        final ForgeConfigSpec.BooleanValue notifyAdminOnLogin;
        final ForgeConfigSpec.BooleanValue afkLogoutEnabled;
        final ForgeConfigSpec.IntValue afkLogoutSeconds;
        final ForgeConfigSpec.ConfigValue<String> welcomeTitleText;
        final ForgeConfigSpec.ConfigValue<String> welcomeSubText;

        Values(ForgeConfigSpec.Builder b) {
            b.comment("yztAuth 服务器配置").push("security");
            minPasswordLength = b.comment("密码最短长度").defineInRange("minPasswordLength", 5, 1, 64);
            maxLoginAttempts  = b.comment("失败锁定阈值（次）").defineInRange("maxLoginAttempts", 4, 1, 100);
            lockoutSeconds    = b.comment("锁定时长（秒）").defineInRange("lockoutSeconds", 90, 1, 86400);
            pbkdf2Iterations  = b.comment("PBKDF2 迭代次数").defineInRange("pbkdf2Iterations", 120000, 10000, 2000000);
            b.pop();

            b.comment("限流与超时").push("limit");
            ipRateLimitCount         = b.comment("IP 限流：窗口内允许次数").defineInRange("ipRateLimit.count", 12, 1, 1000);
            ipRateLimitWindowSeconds = b.comment("IP 限流：窗口长度（秒）").defineInRange("ipRateLimit.windowSeconds", 60, 1, 86400);
            loginTimeoutSeconds      = b.comment("登录超时踢出（秒）").defineInRange("loginTimeoutSeconds", 450, 10, 86400);
            ipResolution             = b.comment("真实 IP 取法：auto | header | remote").define("ipResolution", "auto");
            forwardedHeaderName      = b.comment("代理头名（ipResolution=header/auto 时用）").define("forwardedHeaderName", "X-Forwarded-For");
            b.pop();

            b.comment("登出").push("logout");
            logoutMode            = b.comment("默认登出模式：disconnect | lock | disconnect-throttle").define("logoutMode", "disconnect");
            logoutThrottleSeconds = b.comment("断连禁重连时长（秒）").defineInRange("logoutThrottleSeconds", 10, 0, 3600);
            b.pop();

            b.comment("迁移").push("migrate");
            legacyUsersFile = b.comment("SAuth 用户文件路径").define("legacyUsersFile", "config/serverreg/users.json");
            b.pop();

            b.comment("扩展").push("feature");
            mailMaxPerPlayer   = b.comment("每人私信上限").defineInRange("mailMaxPerPlayer", 20, 1, 1000);
            mailExpireDays     = b.comment("留言超期天数").defineInRange("mailExpireDays", 30, 1, 3650);
            auditLogEnabled    = b.comment("写审计日志 logs/yztauth-audit.log").define("auditLogEnabled", true);
            notifyAdminOnLogin = b.comment("登录通知在线管理员").define("notifyAdminOnLogin", true);
            afkLogoutEnabled   = b.comment("AFK 自动登出（默认关）").define("afkLogoutEnabled", false);
            afkLogoutSeconds   = b.comment("AFK 判定秒数").defineInRange("afkLogoutSeconds", 600, 30, 86400);
            welcomeTitleText   = b.comment("自定义欢迎标题（空=用语言键）").define("welcomeTitleText", "");
            welcomeSubText     = b.comment("自定义欢迎副标题（空=用语言键，%s=玩家名）").define("welcomeSubText", "");
            b.pop();
        }
    }
}
