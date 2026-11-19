package com.yztc.auth.lockdown;

import com.yztc.auth.auth.AuthHolder;
import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.PendingState;
import com.yztc.auth.ui.Defer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Set;

public final class LockdownRules {
    private LockdownRules() {}

    private static final Set<String> ALLOWED_BARE = Set.of("login", "register");
    private static final Set<String> ALLOWED_SUB = Set.of("gate", "login", "register", "help");

    /** 纯逻辑：未登录玩家仅允许认证入口指令，其余一律拦截。 */
    public static boolean allowedWhileLocked(String rawCommand) {
        if (rawCommand == null) return false;
        String s = rawCommand.trim();
        if (s.startsWith("/")) s = s.substring(1).trim();
        if (s.isEmpty()) return false;
        String[] parts = s.split("\\s+");
        String head = parts[0].toLowerCase(Locale.ROOT);
        int colon = head.indexOf(':');
        if (colon >= 0) head = head.substring(colon + 1);
        if (ALLOWED_BARE.contains(head)) return true;
        if (head.equals("yzts")) {
            if (parts.length < 2) return false;
            return ALLOWED_SUB.contains(parts[1].toLowerCase(Locale.ROOT));
        }
        return false;
    }

    public static void tick(ServerPlayer p) {
        AuthManager auth = AuthHolder.AUTH;
        if (auth == null) return;
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null) {
            auth.onAuthenticatedTick(p); // 已认证玩家：AFK 判定
            Defer.runDue(p);
            return;
        }
        auth.onTick(p);
        if (st.disconnecting) return;
        Defer.runDue(p);
        if (PendingState.ALL.get(p.getUUID()) != st) return;
        double dx = p.getX() - st.originX, dy = p.getY() - st.originY, dz = p.getZ() - st.originZ;
        if (dx * dx + dy * dy + dz * dz > 0.01) {
            p.teleportTo(st.originX, st.originY, st.originZ);
            p.setYRot(st.originYaw);
            p.setXRot(st.originPitch);
        }
    }
}
