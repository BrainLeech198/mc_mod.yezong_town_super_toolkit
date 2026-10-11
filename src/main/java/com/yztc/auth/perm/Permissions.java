package com.yztc.auth.perm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
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
    private static Method mGetProvider, mGetUserManager, mGetUser, mGetCachedData, mGetPermissionData, mCheckPermission, mAsBoolean;

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
            Object result = mCheckPermission.invoke(permData, node);
            return (Boolean) mAsBoolean.invoke(result);
        } catch (Throwable t) {
            LOGGER.warn("LuckPerms 查询失败，回退 OP 判定：{}", t.toString());
            return null;
        }
    }

    private static synchronized boolean initLuckPerms() {
        if (lpChecked) return lpPresent;
        lpChecked = true;
        if (!ModList.get().isLoaded("luckperms")) return lpPresent = false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Class<?> userManagerCls = Class.forName("net.luckperms.api.model.user.UserManager");
            Class<?> userCls = Class.forName("net.luckperms.api.model.user.User");
            Class<?> cachedCls = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData");
            Class<?> resultCls = Class.forName("net.luckperms.api.model.data.Tristate");
            // checkPermission 返回 Tristate，asBoolean() 才是最终判定
            Class<?> cachedDataCls = Class.forName("net.luckperms.api.cacheddata.CachedData");

            mGetProvider = provider.getMethod("get");
            mGetUserManager = provider.getMethod("getUserManager");
            mGetUser = userManagerCls.getMethod("getUser", UUID.class);

            mGetCachedData = userCls.getMethod("getCachedData");
            mGetPermissionData = cachedDataCls.getMethod("getPermissionData");
            mCheckPermission = cachedCls.getMethod("checkPermission", String.class);
            mAsBoolean = resultCls.getMethod("asBoolean");
            lpPresent = true;
        } catch (Throwable t) {
            LOGGER.warn("LuckPerms API 反射初始化失败，全部回退 OP 判定：{}", t.toString());
            lpPresent = false;
        }
        return lpPresent;
    }
}
