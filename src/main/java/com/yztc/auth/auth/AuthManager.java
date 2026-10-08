package com.yztc.auth.auth;

import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lockdown.LockdownSync;
import com.yztc.auth.security.PasswordHasher;
import com.yztc.auth.ui.WelcomeSync;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("yztauth");
    private static final Base64.Encoder ENC = Base64.getEncoder();
    private static final Base64.Decoder DEC = Base64.getDecoder();

    /** 未登录提示标题的重发间隔（毫秒），配合短动画形成持续闪烁。 */
    public static final long TITLE_FLASH_INTERVAL_MS = 1500L;

    private final AccountStore store;
    private final GateManager gate;
    private final Set<UUID> authenticated = new HashSet<>();
    private RateLimiter limiter;
    private final LogoutThrottle throttle = new LogoutThrottle();

    public AuthManager(AccountStore store, GateManager gate) {
        this.store = store;
        this.gate = gate;
    }

    public void load() {
        rebuildLimiter();
        authenticated.clear();
        throttle.clear();
    }

    /**
     * 纯逻辑：是否必须先过全服闸门。gatePassed 必须被真正读取，否则闸门形同虚设。
     * 已登录者不受闸门约束；未过闸且未登录者必须过闸。
     */
    public static boolean gateRequired(boolean gateSet, boolean authenticated, boolean gatePassed) {
        return gateSet && !authenticated && !gatePassed;
    }

    /** 纯逻辑：距上次重发是否已到闪烁间隔。 */
    public static boolean flashDue(long nowMs, long lastMs, long intervalMs) {
        return nowMs - lastMs >= intervalMs;
    }

    /** 纯逻辑：还可尝试次数（不为负）。 */
    public static int remainingAttempts(int failed, int max) {
        return Math.max(0, max - failed);
    }

    /** 纯逻辑：AFK 是否超时（功能关闭时恒 false）。 */
    public static boolean afkExpired(long lastActivity, long now, long limitMs, boolean enabled) {
        return enabled && now - lastActivity >= limitMs;
    }

    /** 纯逻辑：登录 IP 是否变更（首次登录 lastIp 为空，不算变更）。 */
    public static boolean ipChanged(String lastIp, String currentIp) {
        return lastIp != null && !lastIp.equals(currentIp);
    }

    /** 该玩家此刻是否必须先过闸。 */
    public boolean gateRequired(ServerPlayer p) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        return gateRequired(gate.isGateSet(), authenticated.contains(p.getUUID()),
                st != null && st.gatePassed);
    }

    /** 闸门验密（含 IP 限流）。 */
    public GateManager.GateResult gateAttempt(ServerPlayer p, String password) {
        return GateManager.attempt(limiter, resolveIp(p), System.currentTimeMillis(), gate.isGateSet(),
                () -> gate.verify(p, password));
    }

    private void rebuildLimiter() {
        limiter = new RateLimiter(AuthConfig.ipRateLimitCount(),
                AuthConfig.ipRateLimitWindowSeconds() * 1000L);
    }

    public boolean isAuthenticated(ServerPlayer p) { return authenticated.contains(p.getUUID()); }
    public boolean isPending(ServerPlayer p) { return PendingState.ALL.containsKey(p.getUUID()); }

    public void onJoin(ServerPlayer p) {
        authenticated.remove(p.getUUID());
        PendingState st = new PendingState(p);
        PendingState.ALL.put(p.getUUID(), st);
        if (throttle.blocked(p.getUUID(), System.currentTimeMillis())) {
            p.connection.disconnect(Lang.msg("logout_ok"));
            return;
        }
        String ip = resolveIp(p);
        LOGGER.info("[yztauth] {} 进服 ip={} gate={}", p.getGameProfile().getName(), ip, gate.isGateSet());
        if (gate.isGateSet()) {
            st.gatePassed = false;
            sendGatePrompt(p);
        } else {
            st.gatePassed = true;
            sendLoginPrompt(p);
        }
    }

    public void onLeave(ServerPlayer p) {
        PendingState.ALL.remove(p.getUUID());
        authenticated.remove(p.getUUID());
    }

    public void onTick(ServerPlayer p) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null) return;
        long now = System.currentTimeMillis();
        long limit = AuthConfig.loginTimeoutSeconds() * 1000L;
        if (now - st.joinedAt > limit) {
            p.connection.disconnect(Lang.msg("timeout"));
            PendingState.ALL.remove(p.getUUID());
            return;
        }
        if (flashDue(now, st.lastTitleMs, TITLE_FLASH_INTERVAL_MS)) {
            st.lastTitleMs = now;
            long remainSec = Math.max(0, (limit - (now - st.joinedAt)) / 1000L);
            WelcomeSync.resendTitle(p, this, remainSec);
        }
    }

    public enum LoginResult { OK, BAD, LOCKED, RATE_LIMITED, NO_ACCOUNT }

    public LoginResult login(ServerPlayer p, String password) {
        String ip = resolveIp(p);
        if (!limiter.tryAcquire(ip, System.currentTimeMillis())) {
            return LoginResult.RATE_LIMITED;
        }
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        Account a = store.get(name);
        if (a == null) return LoginResult.NO_ACCOUNT;

        long now = System.currentTimeMillis();
        if (a.lockedUntil > now) return LoginResult.LOCKED;

        boolean ok;
        if (PasswordHasher.ALGO_LEGACY.equals(a.algo)) {
            ok = PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(password.toCharArray()), a.hash);
        } else {
            ok = PasswordHasher.verifyPbkdf2(password.toCharArray(), a.iterations, DEC.decode(a.salt), DEC.decode(a.hash));
        }

        if (!ok) {
            a.failedAttempts++;
            if (a.failedAttempts >= AuthConfig.maxLoginAttempts()) {
                a.lockedUntil = now + AuthConfig.lockoutSeconds() * 1000L;
                a.failedAttempts = 0;
            }
            persist();
            return LoginResult.BAD;
        }

        // 成功：必要时升级 legacy → PBKDF2
        if (PasswordHasher.ALGO_LEGACY.equals(a.algo)) {
            PasswordHasher.Hashed h = PasswordHasher.pbkdf2(password.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
            a.algo = PasswordHasher.ALGO_PBKDF2;
            a.iterations = h.iterations();
            a.salt = ENC.encodeToString(h.salt());
            a.hash = ENC.encodeToString(h.hash());
            a.migratedAt = null;
            LOGGER.info("[yztauth] {} UPGRADED legacy->pbkdf2", name);
        }
        a.failedAttempts = 0;
        a.lockedUntil = 0;
        a.lastLogin = now;
        persist();

        completeAuth(p);
        limiter.reset(ip);
        return LoginResult.OK;
    }

    public LoginResult register(ServerPlayer p, String password) {
        if (password.length() < AuthConfig.minPasswordLength()) return LoginResult.BAD;
        String ip = resolveIp(p);
        if (!limiter.tryAcquire(ip, System.currentTimeMillis())) return LoginResult.RATE_LIMITED;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        if (store.contains(name)) return LoginResult.NO_ACCOUNT; // 已存在 → 让调用方提示"去登录"

        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(password.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        Account a = new Account();
        a.algo = PasswordHasher.ALGO_PBKDF2;
        a.iterations = h.iterations();
        a.salt = ENC.encodeToString(h.salt());
        a.hash = ENC.encodeToString(h.hash());
        a.registeredAt = System.currentTimeMillis();
        a.lastLogin = a.registeredAt;
        store.put(name, a);
        persist();

        completeAuth(p);
        limiter.reset(ip);
        return LoginResult.OK;
    }

    public void logout(ServerPlayer p, String mode) {
        authenticated.remove(p.getUUID());
        PendingState st = new PendingState(p); // 以当前位置为新原点
        PendingState.ALL.put(p.getUUID(), st);
        st.gatePassed = true; // 已过闸者登出后不必再过闸
        String ip = resolveIp(p);
        switch (mode) {
            case "lock" -> sendLoginPrompt(p);
            case "disconnect-throttle" -> {
                throttle.mark(p.getUUID(), System.currentTimeMillis(), AuthConfig.logoutThrottleSeconds());
                p.connection.disconnect(Lang.msg("logout_ok"));
            }
            default -> p.connection.disconnect(Lang.msg("logout_ok"));
        }
        limiter.reset(ip);
    }

    public boolean changePassword(ServerPlayer p, String oldPw, String newPw) {
        if (newPw.length() < AuthConfig.minPasswordLength()) return false;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        Account a = store.get(name);
        if (a == null) return false;
        boolean ok = PasswordHasher.ALGO_LEGACY.equals(a.algo)
                ? PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(oldPw.toCharArray()), a.hash)
                : PasswordHasher.verifyPbkdf2(oldPw.toCharArray(), a.iterations, DEC.decode(a.salt), DEC.decode(a.hash));
        if (!ok) return false;
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(newPw.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        a.algo = PasswordHasher.ALGO_PBKDF2;
        a.iterations = h.iterations();
        a.salt = ENC.encodeToString(h.salt());
        a.hash = ENC.encodeToString(h.hash());
        persist();
        return true;
    }

    /** 由命令/面板/控制台调用的、对目标玩家直接落认证态的入口。 */
    public void forceAuthenticate(ServerPlayer p) {
        completeAuth(p);
    }

    public void forceLogout(ServerPlayer p, String mode) {
        logout(p, mode);
    }

    public Account accountOf(String nameLower) { return store.get(nameLower); }
    public void persistNow() { persist(); }

    /** 用某玩家自己的当前密码做二次验密（危险操作的守卫）。 */
    public boolean verifyOwnPassword(ServerPlayer p, String password) {
        return verifyPassword(p.getGameProfile().getName().toLowerCase(Locale.ROOT), password);
    }

    /** 校验某账号的密码（不改变任何状态、不计数失败），便于自测与复用。 */
    public boolean verifyPassword(String nameLower, String password) {
        Account a = store.get(nameLower);
        if (a == null) return false;
        if (PasswordHasher.ALGO_LEGACY.equals(a.algo)) {
            return PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(password.toCharArray()), a.hash);
        }
        return PasswordHasher.verifyPbkdf2(password.toCharArray(), a.iterations,
                DEC.decode(a.salt), DEC.decode(a.hash));
    }

    private void completeAuth(ServerPlayer p) {
        authenticated.add(p.getUUID());
        PendingState.ALL.remove(p.getUUID());
        LockdownSync.authenticate(p);
        WelcomeSync.welcome(p);
    }

    public String resolveIp(ServerPlayer p) {
        String mode = AuthConfig.ipResolution();
        String remote = p.connection.getRemoteAddress() == null
                ? "unknown" : p.connection.getRemoteAddress().toString();
        if ("remote".equalsIgnoreCase(mode)) return remote;
        // auto / header：尝试代理头
        String header = AuthConfig.forwardedHeaderName();
        String fromHeader = HeaderProbe.firstValue(p, header);
        if (fromHeader != null && !fromHeader.isBlank()) return fromHeader.trim();
        return remote;
    }

    private void persist() {
        try {
            store.save();
        } catch (IOException e) {
            LOGGER.error("[yztauth] users.json 写入失败，保留旧文件", e);
        }
    }

    private void sendGatePrompt(ServerPlayer p) {
        WelcomeSync.promptGate(p);
    }

    private void sendLoginPrompt(ServerPlayer p) {
        WelcomeSync.promptLogin(p, this);
    }
}
