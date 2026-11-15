package com.yztc.auth.auth;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.Gate;
import com.yztc.auth.security.PasswordHasher;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

public final class GateManager {
    private static final java.util.Base64.Encoder ENC = java.util.Base64.getEncoder();

    private final AccountStore store;

    public GateManager(AccountStore store) {
        this.store = store;
    }

    public enum GateResult { OK, BAD, NOT_SET, RATE_LIMITED }

    public boolean isGateSet() {
        return store.getGate() != null;
    }

    /**
     * 纯逻辑：闸门验密 = 先过 IP 限流（与登录共享配置），再验密；成功即重置该 IP。
     * 未设闸门时不消耗额度。抽成静态函数便于自测与复用。
     */
    public static GateResult attempt(RateLimiter limiter, String ip, long now, boolean gateSet,
                                     java.util.function.Supplier<GateResult> verify) {
        if (!gateSet) return GateResult.NOT_SET;
        if (!limiter.tryAcquire(ip, now)) return GateResult.RATE_LIMITED;
        GateResult r = verify.get();
        if (r == GateResult.OK) limiter.reset(ip);
        return r;
    }

    public GateResult verify(ServerPlayer player, String password) {
        Gate g = store.getGate();
        if (g == null) return GateResult.NOT_SET;
        boolean ok;
        if (PasswordHasher.ALGO_PBKDF2.equals(g.algo)) {
            ok = PasswordHasher.verifyPbkdf2Encoded(
                    password.toCharArray(), g.iterations, g.salt, g.hash);
        } else if (PasswordHasher.ALGO_LEGACY.equals(g.algo)) {
            ok = PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(password.toCharArray()), g.hash);
        } else {
            ok = false;
        }
        if (ok && PendingState.ALL.containsKey(player.getUUID())) {
            PendingState.ALL.get(player.getUUID()).gatePassed = true;
        }
        return ok ? GateResult.OK : GateResult.BAD;
    }

    public void set(String newPassword) throws IOException {
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(newPassword.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        Gate g = new Gate();
        g.algo = PasswordHasher.ALGO_PBKDF2;
        g.iterations = h.iterations();
        g.salt = ENC.encodeToString(h.salt());
        g.hash = ENC.encodeToString(h.hash());
        g.updatedAt = System.currentTimeMillis();
        store.setGate(g);
        store.save();
    }

    public void clear() throws IOException {
        store.clearGate();
        store.save();
    }
}
