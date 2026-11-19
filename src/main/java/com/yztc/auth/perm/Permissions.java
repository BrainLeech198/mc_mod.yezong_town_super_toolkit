package com.yztc.auth.perm;

import net.minecraft.server.level.ServerPlayer;
import com.yztc.auth.ui.ModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.UUID;

public final class Permissions {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");

    public static final String ADMIN = "yzt_super.admin";
    public static final String GATE = "yzt_super.gate";
    public static final String BYPASS_CONFIRM = "yzt_super.bypass.confirm";

    private static boolean lpChecked = false;
    private static boolean lpPresent = false;
    private static Method mGetProvider, mGetUserManager, mGetUser, mGetCachedData, mGetPermissionData, mCheckPermission;

    private Permissions() {}

    public static boolean has(ServerPlayer p, String node, int fallbackOpLevel) {
        Boolean lpResult = queryLuckPerms(p.getUUID(), node);
        if (lpResult != null) return lpResult;
        return p.hasPermissions(fallbackOpLevel);
    }

    public static boolean isAdmin(ServerPlayer p) { return has(p, ADMIN, 2); }
    public static boolean isGateKeeper(ServerPlayer p) { return has(p, GATE, 4); }

    private static Boolean queryLuckPerms(UUID uuid, String node) {
        if (!initLuckPerms()) return null;
        try {
            Object lp = mGetProvider.invoke(null);
            Object userManager = mGetUserManager.invoke(lp);
            Object user = mGetUser.invoke(userManager, uuid);
            if (user == null) return null;
            Object cached = mGetCachedData.invoke(user);
            Object permData = mGetPermissionData.invoke(cached);
            Enum<?> result = (Enum<?>) mCheckPermission.invoke(permData, node);
            return switch (result.name()) {
                case "TRUE" -> true;
                case "FALSE" -> false;
                case "UNDEFINED" -> null;
                default -> throw new IllegalStateException("Unexpected LuckPerms Tristate: " + result.name());
            };
        } catch (Throwable t) {
            LOGGER.warn("LuckPerms 查询失败，回退 OP 判定：{}", t.toString());
            return null;
        }
    }

    private static synchronized boolean initLuckPerms() {
        if (lpChecked) return lpPresent;
        lpChecked = true;
        if (!ModInfo.isLoaded("luckperms")) return lpPresent = false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            mGetProvider = provider.getMethod("get");
            mGetUserManager = mGetProvider.getReturnType().getMethod("getUserManager");
            mGetUser = mGetUserManager.getReturnType().getMethod("getUser", UUID.class);
            mGetCachedData = mGetUser.getReturnType().getMethod("getCachedData");
            mGetPermissionData = mGetCachedData.getReturnType().getMethod("getPermissionData");
            mCheckPermission = mGetPermissionData.getReturnType().getMethod("checkPermission", String.class);
            lpPresent = true;
        } catch (Throwable t) {
            LOGGER.warn("LuckPerms API 反射初始化失败，全部回退 OP 判定：{}", t.toString());
            lpPresent = false;
        }
        return lpPresent;
    }
}
