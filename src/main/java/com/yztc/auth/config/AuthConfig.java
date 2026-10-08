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
        }
    }
}
