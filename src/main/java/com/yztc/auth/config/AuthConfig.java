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
    public static boolean restoreLastGameMode()     { return V.restoreLastGameMode.get(); }
    public static boolean afkLogoutEnabled()       { return V.afkLogoutEnabled.get(); }
    public static int afkLogoutSeconds()           { return V.afkLogoutSeconds.get(); }
    public static boolean afkAnnounceEnabled()     { return V.afkAnnounceEnabled.get(); }
    public static int afkAnnounceSeconds()         { return V.afkAnnounceSeconds.get(); }
    public static String welcomeTitleText()        { return V.welcomeTitleText.get(); }
    public static String welcomeSubText()          { return V.welcomeSubText.get(); }
    public static boolean startupBanner()          { return V.startupBanner.get(); }
    public static String serverName()              { return V.serverName.get(); }
    public static String language()                { return V.language.get(); }
    public static int broadcastCooldownSeconds()    { return V.broadcastCooldownSeconds.get(); }
    public static boolean broadcastAllowPlayerColors(){ return V.broadcastAllowPlayerColors.get(); }
    public static String broadcastPrefix()          { return V.broadcastPrefix.get(); }
    public static boolean broadcastChannelChat()    { return V.broadcastChannelChat.get(); }
    public static boolean broadcastChannelTitle()   { return V.broadcastChannelTitle.get(); }
    public static boolean broadcastChannelSubtitle(){ return V.broadcastChannelSubtitle.get(); }
    public static boolean broadcastChannelActionbar(){ return V.broadcastChannelActionbar.get(); }
    public static int broadcastMaxLength()          { return V.broadcastMaxLength.get(); }

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
        final ForgeConfigSpec.BooleanValue restoreLastGameMode;
        final ForgeConfigSpec.BooleanValue afkLogoutEnabled;
        final ForgeConfigSpec.IntValue afkLogoutSeconds;
        final ForgeConfigSpec.BooleanValue afkAnnounceEnabled;
        final ForgeConfigSpec.IntValue afkAnnounceSeconds;
        final ForgeConfigSpec.ConfigValue<String> welcomeTitleText;
        final ForgeConfigSpec.ConfigValue<String> welcomeSubText;
        final ForgeConfigSpec.BooleanValue startupBanner;
        final ForgeConfigSpec.ConfigValue<String> serverName;
        final ForgeConfigSpec.ConfigValue<String> language;
        final ForgeConfigSpec.IntValue broadcastCooldownSeconds;
        final ForgeConfigSpec.BooleanValue broadcastAllowPlayerColors;
        final ForgeConfigSpec.ConfigValue<String> broadcastPrefix;
        final ForgeConfigSpec.BooleanValue broadcastChannelChat;
        final ForgeConfigSpec.BooleanValue broadcastChannelTitle;
        final ForgeConfigSpec.BooleanValue broadcastChannelSubtitle;
        final ForgeConfigSpec.BooleanValue broadcastChannelActionbar;
        final ForgeConfigSpec.IntValue broadcastMaxLength;

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
            ipResolution             = b.comment("客户端地址：auto（可信 Forge 适配器优先，否则连接地址）| remote（强制连接地址）；header 为兼容别名").define("ipResolution", "auto");
            forwardedHeaderName      = b.comment("旧配置兼容项；Minecraft 连接不是 HTTP，不读取此转发头").define("forwardedHeaderName", "X-Forwarded-For");
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
            auditLogEnabled    = b.comment("写审计日志 logs/yzt_super-audit.log").define("auditLogEnabled", true);
            notifyAdminOnLogin = b.comment("登录通知在线管理员").define("notifyAdminOnLogin", true);
            restoreLastGameMode = b.comment("玩家密码验证成功后恢复其上次退出时的游戏模式").define("restoreLastGameMode", true);
            afkLogoutEnabled   = b.comment("AFK 自动登出（默认关）").define("afkLogoutEnabled", false);
            afkLogoutSeconds   = b.comment("AFK 判定秒数").defineInRange("afkLogoutSeconds", 600, 30, 86400);
            welcomeTitleText   = b.comment("自定义欢迎标题（空=用语言键）").define("welcomeTitleText", "");
            welcomeSubText     = b.comment("自定义欢迎副标题（空=用语言键，%s=玩家名）").define("welcomeSubText", "");
            startupBanner      = b.comment("开服时打印启动横幅（控制台 + 首个管理员进服）").define("startupBanner", true);
            serverName         = b.comment("横幅品牌行显示的服务器名（空=不显示品牌行）").define("serverName", "");
            language           = b.comment("服务器兜底语言（原版客户端显示用）：zh_cn | en_us").define("language", "zh_cn");
            b.pop();

            b.comment("挂机公告").push("afkAnnounce");
            afkAnnounceEnabled = b.comment("挂机/返回全服公告（默认开）").define("enabled", true);
            afkAnnounceSeconds = b.comment("挂机判定秒数（超过则在聊天栏全服公告）").defineInRange("seconds", 180, 30, 86400);
            b.pop();

            b.comment("大喇叭").push("broadcast");
            broadcastCooldownSeconds   = b.comment("玩家喊话冷却（秒）").defineInRange("cooldownSeconds", 60, 0, 3600);
            broadcastAllowPlayerColors = b.comment("是否允许玩家在喊话里使用颜色码").define("allowPlayerColors", false);
            broadcastPrefix            = b.comment("广播前缀（空=不显示）").define("prefix", "[公告]");
            broadcastChannelChat       = b.comment("管理员公告：发聊天栏").define("channel.chat", true);
            broadcastChannelTitle      = b.comment("管理员公告：发屏幕标题").define("channel.title", true);
            broadcastChannelSubtitle   = b.comment("管理员公告：发屏幕副标题").define("channel.subtitle", false);
            broadcastChannelActionbar  = b.comment("管理员公告：发 actionbar").define("channel.actionbar", false);
            broadcastMaxLength         = b.comment("广播内容长度上限（字符）").defineInRange("maxLength", 128, 1, 512);
            b.pop();
        }
    }
}
