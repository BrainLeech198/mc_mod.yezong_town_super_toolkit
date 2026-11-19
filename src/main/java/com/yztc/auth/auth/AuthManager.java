package com.yztc.auth.auth;

import com.yztc.auth.audit.AuditLog;
import com.yztc.auth.api.ClientIpResolveEvent;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.broadcast.AfkBroadcast;
import com.yztc.auth.data.Account;
import com.yztc.auth.data.AccountStore;
import com.yztc.auth.data.LastSeen;
import com.yztc.auth.data.LastSeenStore;
import com.yztc.auth.data.StoreHolder;
import com.yztc.auth.lang.Lang;
import com.yztc.auth.lockdown.LockdownSync;
import com.yztc.auth.perm.Permissions;
import com.yztc.auth.security.PasswordHasher;
import com.yztc.auth.ui.TabHider;
import com.yztc.auth.ui.AuthGui;
import com.yztc.auth.ui.Defer;
import com.yztc.auth.ui.Sounds;
import com.yztc.auth.ui.WelcomeSync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AuthManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("yzt_super");
    private static final Base64.Encoder ENC = Base64.getEncoder();

    /** 未登录提示标题的重发间隔（毫秒），配合短动画形成持续闪烁。 */
    public static final long TITLE_FLASH_INTERVAL_MS = 1500L;

    private final AccountStore store;
    private final GateManager gate;
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, Long> lastActivityMs = new HashMap<>();
    private final Map<UUID, double[]> prevTickPose = new HashMap<>();
    private RateLimiter limiter;
    private final LogoutThrottle throttle = new LogoutThrottle();

    public AuthManager(AccountStore store, GateManager gate) {
        this.store = store;
        this.gate = gate;
    }

    public void load() {
        rebuildLimiter();
        authenticated.clear();
        lastActivityMs.clear();
        prevTickPose.clear();
        throttle.clear();
        AfkBroadcast.clearAll();
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

    /** 单 tick 水平位移阈值（方块）：被推动是 0.05 冲量+地面摩擦（稳态≈0.11），行走≈0.216，阈值须卡在两者之间。 */
    public static final double WALK_TICK_LIMIT = 0.15;

    /** 单 tick 转向阈值（度）：被动位移不改朝向，小阈值即可。 */
    public static final float TURN_TICK_LIMIT = 0.1f;

    /** 纯逻辑：单 tick 水平位移是否达到行走量级（被推动等被动位移上不了这个量级）。 */
    public static boolean walkedTick(double dx, double dz) {
        return Math.hypot(dx, dz) > WALK_TICK_LIMIT;
    }

    /** 纯逻辑：单 tick 转向是否超过阈值。 */
    public static boolean turnedTick(float dYaw, float dPitch) {
        return Math.abs(dYaw) > TURN_TICK_LIMIT || Math.abs(dPitch) > TURN_TICK_LIMIT;
    }

    /** 纯逻辑：真实操作 = 转向 / 行走级位移 / 载具操控；被推动等被动位移不算「人回来了」。 */
    public static boolean realActivity(boolean turned, boolean walked, boolean steering) {
        return turned || walked || steering;
    }

    /** 纯逻辑：该登出模式是否保留连接（回到未登录态，需重新从他人 Tab 隐藏）。 */
    public static boolean staysConnectedOnLogout(String mode) {
        return "lock".equals(mode);
    }

    /** 该玩家此刻是否必须先过闸。 */
    public boolean gateRequired(ServerPlayer p) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        return gateRequired(gate.isGateSet(), authenticated.contains(p.getUUID()),
                st != null && st.gatePassed);
    }

    /** 闸门验密（含 IP 限流）。 */
    public GateManager.GateResult gateAttempt(ServerPlayer p, String password) {
        String ip = resolveIp(p);
        GateManager.GateResult r = GateManager.attempt(limiter, ip, System.currentTimeMillis(), gate.isGateSet(),
                () -> gate.verify(p, password));
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        switch (r) {
            case OK -> { audit(name, ip, "GATE_OK"); Sounds.ok(p); }
            case BAD -> { audit(name, ip, "GATE_FAIL"); Sounds.fail(p); }
            case RATE_LIMITED -> { audit(name, ip, "GATE_RATE_LIMITED"); Sounds.fail(p); }
            default -> { }
        }
        return r;
    }

    private void rebuildLimiter() {
        limiter = new RateLimiter(AuthConfig.ipRateLimitCount(),
                AuthConfig.ipRateLimitWindowSeconds() * 1000L);
    }

    public boolean isAuthenticated(ServerPlayer p) { return authenticated.contains(p.getUUID()); }
    public boolean isPending(ServerPlayer p) { return PendingState.ALL.containsKey(p.getUUID()); }

    public boolean closingSession(ServerPlayer p) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        return p.hasDisconnected() || (st != null && (st.player != p || st.disconnecting));
    }

    public void onJoin(ServerPlayer p) {
        clearInputs(p);
        authenticated.remove(p.getUUID());
        PendingState st = new PendingState(p);
        PendingState.ALL.put(p.getUUID(), st);
        if (throttle.blocked(p.getUUID(), System.currentTimeMillis())) {
            st.disconnecting = true;
            p.connection.disconnect(Lang.msg("logout_ok"));
            return;
        }
        String ip = resolveIp(p);
        LOGGER.info("[yzt_super] {} 进服 ip={} gate={}", p.getGameProfile().getName(), ip, gate.isGateSet());
        if (gate.isGateSet()) {
            st.gatePassed = false;
            sendGatePrompt(p);
        } else {
            st.gatePassed = true;
            sendLoginPrompt(p);
        }
        // 未认证时自动弹铁砧输入框（原版客户端亦可；Esc 关掉后仍可点聊天栏按钮重开）。
        // execute 在服务端主线程可能同步执行；明确排到下一 tick，等客户端完成进服界面初始化。
        Defer.nextTick(p, () -> {
            if (isPending(p)) AuthGui.openFor(p, this);
        });
        // B4：未登录者从他人 Tab 隐藏，同时自己也看不到别的未登录者
        TabHider.hideFromOthers(p);
        TabHider.hidePendingFrom(p);
        // D1：未注册的新玩家首次进服，全服欢迎
        if (store.get(p.getGameProfile().getName().toLowerCase(Locale.ROOT)) == null) {
            MinecraftServer server = p.server;
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        WelcomeSync.firstJoinMessage(p.getGameProfile().getName()), false);
            }
        }
    }

    private static void clearInputs(ServerPlayer p) {
        Defer.forget(p);
        com.yztc.auth.ui.UiSession.forget(p.getUUID());
        com.yztc.auth.ui.InputPrompt.forget(p.getUUID());
    }

    public void onLeave(ServerPlayer p) {
        clearInputs(p);
        PendingState.ALL.remove(p.getUUID());
        authenticated.remove(p.getUUID());
        lastActivityMs.remove(p.getUUID());
        prevTickPose.remove(p.getUUID());
        AfkBroadcast.forget(p.getUUID());
    }

    /** 活动刷新：仅真实操作（转向 / 行走级位移 / 载具操控）刷新活动时间；被推动等被动位移不算操作。 */
    public void onAuthenticatedTick(ServerPlayer p) {
        if (!authenticated.contains(p.getUUID())) return;
        long now = System.currentTimeMillis();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        float yaw = p.getYRot(), pitch = p.getXRot();
        double[] prev = prevTickPose.get(p.getUUID());
        boolean acted;
        if (prev == null) {
            acted = true;
        } else {
            boolean turned = turnedTick(yaw - (float) prev[3], pitch - (float) prev[4]);
            boolean walked = !p.isPassenger() && walkedTick(x - prev[0], z - prev[2]);
            boolean steer = p.isPassenger() && (p.xxa != 0f || p.zza != 0f);
            acted = realActivity(turned, walked, steer);
        }
        prevTickPose.put(p.getUUID(), new double[]{x, y, z, yaw, pitch});
        if (acted) {
            lastActivityMs.put(p.getUUID(), now);
        }
        long lastMs = lastActivityMs.getOrDefault(p.getUUID(), now);
        // 挂机/返回全服公告：独立于 AFK 自动登出（阈值更短，只播报不踢人）
        AfkBroadcast.tick(p, acted, now - lastMs, AuthConfig.afkAnnounceSeconds() * 1000L,
                AuthConfig.afkAnnounceEnabled());
        if (afkExpired(lastMs, now, AuthConfig.afkLogoutSeconds() * 1000L, AuthConfig.afkLogoutEnabled())) {
            p.sendSystemMessage(Lang.msg("afk_logout"));
            logout(p, AuthConfig.logoutMode());
        }
    }

    public void onTick(ServerPlayer p) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null || st.disconnecting) return;
        long now = System.currentTimeMillis();
        long limit = AuthConfig.loginTimeoutSeconds() * 1000L;
        if (now - st.joinedAt > limit) {
            st.disconnecting = true;
            p.connection.disconnect(Lang.msg("timeout"));
            return;
        }
        if (flashDue(now, st.lastTitleMs, TITLE_FLASH_INTERVAL_MS)) {
            st.lastTitleMs = now;
            long remainSec = Math.max(0, (limit - (now - st.joinedAt)) / 1000L);
            WelcomeSync.resendTitle(p, this, remainSec);
        }
    }

    public enum LoginResult { OK, BAD, LOCKED, RATE_LIMITED, NO_ACCOUNT, SAVE_FAILED }
    public enum RegisterResult { OK, INVALID_PASSWORD, ALREADY_REGISTERED, RATE_LIMITED, SAVE_FAILED }
    public enum PasswordChangeResult { CHANGED, INVALID_NEW_PASSWORD, NO_ACCOUNT, BAD_OLD_PASSWORD, SAVE_FAILED }

    public LoginResult login(ServerPlayer p, String password) {
        String ip = resolveIp(p);
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        if (!limiter.tryAcquire(ip, System.currentTimeMillis())) {
            audit(name, ip, "LOGIN_RATE_LIMITED");
            Sounds.fail(p);
            return LoginResult.RATE_LIMITED;
        }
        Account previous = store.get(name);
        LoginResult result;
        try {
            result = verifyAndSaveLogin(store, name, password, ip, System.currentTimeMillis());
        } catch (IOException ex) {
            LOGGER.error("[yzt_super] 登录状态未能写入 users.json", ex);
            result = LoginResult.SAVE_FAILED;
        }
        if (result != LoginResult.OK) {
            audit(name, ip, result == LoginResult.BAD ? "LOGIN_FAIL" : "LOGIN_" + result.name());
            Sounds.fail(p);
            return result;
        }
        if (previous.isLegacy()) LOGGER.info("[yzt_super] {} UPGRADED legacy->pbkdf2", name);
        if (ipChanged(previous.lastIp, ip)) p.sendSystemMessage(Lang.msg("login_new_ip", ip));
        audit(name, ip, "LOGIN_OK");
        notifyAdmins(p, name, ip);
        completeAuth(p);
        limiter.reset(ip);
        return LoginResult.OK;
    }

    /** Commit the account transition before the player session or its observers see success. */
    public static LoginResult verifyAndSaveLogin(AccountStore store, String name, String password, String ip, long now)
            throws IOException {
        if (store.loadFailed()) return LoginResult.SAVE_FAILED;
        Account current = store.get(name);
        if (current == null) return LoginResult.NO_ACCOUNT;
        if (current.lockedUntil > now) return LoginResult.LOCKED;

        Account updated = current.copy();
        if (!verifyPassword(current, password)) {
            updated.failedAttempts++;
            if (updated.failedAttempts >= AuthConfig.maxLoginAttempts()) {
                updated.lockedUntil = now + AuthConfig.lockoutSeconds() * 1000L;
                updated.failedAttempts = 0;
            }
            store.putAndSave(name, updated);
            return LoginResult.BAD;
        }
        if (current.isLegacy()) {
            PasswordHasher.Hashed h = PasswordHasher.pbkdf2(password.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
            updated.algo = PasswordHasher.ALGO_PBKDF2;
            updated.iterations = h.iterations();
            updated.salt = ENC.encodeToString(h.salt());
            updated.hash = ENC.encodeToString(h.hash());
            updated.migratedAt = null;
        }
        updated.failedAttempts = 0;
        updated.lockedUntil = 0;
        updated.lastLogin = now;
        updated.lastIp = ip;
        store.putAndSave(name, updated);
        return LoginResult.OK;
    }

    public RegisterResult register(ServerPlayer p, String password) {
        if (password.length() < AuthConfig.minPasswordLength()) { Sounds.fail(p); return RegisterResult.INVALID_PASSWORD; }
        String ip = resolveIp(p);
        if (!limiter.tryAcquire(ip, System.currentTimeMillis())) { Sounds.fail(p); return RegisterResult.RATE_LIMITED; }
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        if (store.contains(name)) { Sounds.fail(p); return RegisterResult.ALREADY_REGISTERED; }

        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(password.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        Account a = new Account();
        a.algo = PasswordHasher.ALGO_PBKDF2;
        a.iterations = h.iterations();
        a.salt = ENC.encodeToString(h.salt());
        a.hash = ENC.encodeToString(h.hash());
        a.registeredAt = System.currentTimeMillis();
        a.lastLogin = a.registeredAt;
        try {
            store.putAndSave(name, a);
        } catch (IOException ex) {
            LOGGER.error("[yzt_super] 注册未能写入 users.json", ex);
            Sounds.fail(p);
            return RegisterResult.SAVE_FAILED;
        }

        completeAuth(p);
        limiter.reset(ip);
        return RegisterResult.OK;
    }

    public void logout(ServerPlayer p, String mode) {
        authenticated.remove(p.getUUID());
        lastActivityMs.remove(p.getUUID());
        prevTickPose.remove(p.getUUID());
        AfkBroadcast.forget(p.getUUID());
        PendingState st = new PendingState(p); // 以当前位置为新原点
        PendingState.ALL.put(p.getUUID(), st);
        st.gatePassed = true; // 已过闸者登出后不必再过闸
        String ip = resolveIp(p);
        p.closeContainer();
        clearInputs(p);
        if (staysConnectedOnLogout(mode)) {
            LockdownSync.lock(p);
            sendLoginPrompt(p);
            // B4：登出回到未登录态，须重新从他人 Tab 隐藏（与进服时一致）
            TabHider.hideFromOthers(p);
            TabHider.hidePendingFrom(p);
        } else if ("disconnect-throttle".equals(mode)) {
            st.disconnecting = true;
            throttle.mark(p.getUUID(), System.currentTimeMillis(), AuthConfig.logoutThrottleSeconds());
            p.connection.disconnect(Lang.msg("logout_ok"));
        } else {
            st.disconnecting = true;
            p.connection.disconnect(Lang.msg("logout_ok"));
        }
        limiter.reset(ip);
    }

    public PasswordChangeResult changePassword(ServerPlayer p, String oldPw, String newPw) {
        if (newPw.length() < AuthConfig.minPasswordLength()) return PasswordChangeResult.INVALID_NEW_PASSWORD;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        Account a = store.get(name);
        if (a == null) return PasswordChangeResult.NO_ACCOUNT;
        boolean ok = verifyPassword(a, oldPw);
        if (!ok) return PasswordChangeResult.BAD_OLD_PASSWORD;
        Account previous = a.copy();
        PasswordHasher.Hashed h = PasswordHasher.pbkdf2(newPw.toCharArray(), AuthConfig.pbkdf2Iterations(), 16);
        a.algo = PasswordHasher.ALGO_PBKDF2;
        a.iterations = h.iterations();
        a.salt = ENC.encodeToString(h.salt());
        a.hash = ENC.encodeToString(h.hash());
        if (!persist()) {
            store.put(name, previous);
            return PasswordChangeResult.SAVE_FAILED;
        }
        Sounds.ok(p);
        return PasswordChangeResult.CHANGED;
    }

    /** 由命令/面板/控制台调用的、对目标玩家直接落认证态的入口。 */
    public void forceAuthenticate(ServerPlayer p) {
        completeAuth(p);
    }

    public void forceLogout(ServerPlayer p, String mode) {
        logout(p, mode);
    }

    public Account accountOf(String nameLower) { return store.get(nameLower); }

    /** 校验账户密码（PBKDF2 或遗留 SHA-256）。 */
    private static boolean verifyPassword(Account a, String pw) {
        if (pw == null) return false;
        if (PasswordHasher.ALGO_LEGACY.equals(a.algo)) {
            return PasswordHasher.verifyLegacy(PasswordHasher.legacySha256Hex(pw.toCharArray()), a.hash);
        }
        return PasswordHasher.ALGO_PBKDF2.equals(a.algo)
                && PasswordHasher.verifyPbkdf2Encoded(pw.toCharArray(), a.iterations, a.salt, a.hash);
    }

    /** 邮箱操作结果。 */
    public enum EmailResult { SET, ALREADY_SET, BAD_OLD_EMAIL, BAD_PASSWORD, NO_ACCOUNT, SAVE_FAILED }

    /**
     * 首次绑定邮箱。已经绑过则返回 {@link EmailResult#ALREADY_SET}——邮箱只能设一次，
     * 再改必须走 {@link #changeEmail}（旧邮箱 + 密码双重验证）。
     */
    public EmailResult bindEmail(ServerPlayer p, String addr) {
        Account a = store.get(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
        if (a == null) return EmailResult.NO_ACCOUNT;
        if (a.email != null && !a.email.isBlank()) return EmailResult.ALREADY_SET;
        String previous = a.email;
        a.email = addr.trim();
        if (!persist()) {
            a.email = previous;
            return EmailResult.SAVE_FAILED;
        }
        return EmailResult.SET;
    }

    /** 改绑邮箱：旧邮箱与账户密码两项都对，才把邮箱换成 {@code newAddr}。 */
    public EmailResult changeEmail(ServerPlayer p, String oldEmail, String oldPw, String newAddr) {
        Account a = store.get(p.getGameProfile().getName().toLowerCase(Locale.ROOT));
        if (a == null) return EmailResult.NO_ACCOUNT;
        if (a.email == null || a.email.isBlank()) return EmailResult.ALREADY_SET; // 未绑定无从验证
        if (!a.email.equalsIgnoreCase(oldEmail == null ? "" : oldEmail.trim())) return EmailResult.BAD_OLD_EMAIL;
        if (!verifyPassword(a, oldPw)) return EmailResult.BAD_PASSWORD;
        String previous = a.email;
        a.email = newAddr.trim();
        if (!persist()) {
            a.email = previous;
            return EmailResult.SAVE_FAILED;
        }
        return EmailResult.SET;
    }
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
        return PasswordHasher.ALGO_PBKDF2.equals(a.algo)
                && PasswordHasher.verifyPbkdf2Encoded(password.toCharArray(), a.iterations, a.salt, a.hash);
    }

    private void completeAuth(ServerPlayer p) {
        authenticated.add(p.getUUID());
        PendingState.ALL.remove(p.getUUID());
        lastActivityMs.put(p.getUUID(), System.currentTimeMillis());
        prevTickPose.remove(p.getUUID());
        AfkBroadcast.forget(p.getUUID()); // 刚刚登录不算「挂机返回」，清掉旧标记
        LockdownSync.authenticate(p);
        restoreLastGameMode(p);
        TabHider.reveal(p);
        com.yztc.auth.title.TitleEvents.refreshDisplay(p);
        WelcomeSync.welcome(p);
        Sounds.ok(p);
        com.yztc.auth.ui.LoginBoard.send(p, p.getGameProfile().getName().toLowerCase(Locale.ROOT));
        // 管理员登录后：若有未处理的高风险记录，额外提醒（含「查看」按钮）
        if (Permissions.isAdmin(p)) com.yztc.auth.risk.RiskLog.notifyIfUnhandled(p);
    }

    /** 只在认证成功后恢复上次模式；旧位置记录没有模式字段时维持当前服务器模式。 */
    private static void restoreLastGameMode(ServerPlayer p) {
        if (!AuthConfig.restoreLastGameMode()) return;
        LastSeenStore lastSeenStore = StoreHolder.lastSeen();
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        LastSeen lastSeen = lastSeenStore == null ? null : lastSeenStore.get(name);
        if (lastSeen == null || lastSeen.gameMode == null) return;

        GameType mode = switch (lastSeen.gameMode.toLowerCase(Locale.ROOT)) {
            case "survival" -> GameType.SURVIVAL;
            case "creative" -> GameType.CREATIVE;
            case "adventure" -> GameType.ADVENTURE;
            case "spectator" -> GameType.SPECTATOR;
            default -> null;
        };
        if (mode != null) p.setGameMode(mode);
    }

    /** 写审计行（受配置开关控制，且仅记 名字/IP/结果词，绝不含明文口令）。 */
    private static void audit(String name, String ip, String event) {
        if (!AuthConfig.auditLogEnabled()) return;
        AuditLog log = StoreHolder.audit();
        if (log == null) return;
        log.append(AuditLog.line(AuditLog.nowIso(), name, ip, event));
    }

    /** B2：登录成功后通知在线管理员（受配置开关控制）。 */
    private void notifyAdmins(ServerPlayer p, String name, String ip) {
        if (!AuthConfig.notifyAdminOnLogin()) return;
        MinecraftServer server = p.server;
        if (server == null) return;
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (admin.getUUID().equals(p.getUUID())) continue;
            if (Permissions.isAdmin(admin)) admin.sendSystemMessage(Lang.msg("admin_login_notice", name, ip));
        }
    }

    public String resolveIp(ServerPlayer p) {
        String mode = AuthConfig.ipResolution();
        SocketAddress remoteAddress = p.connection.getRemoteAddress();
        String remote = "unknown";
        if (remoteAddress instanceof InetSocketAddress inet) {
            // SocketAddress.toString() includes the client's ephemeral source port;
            // using it as the rate-limit key would give every reconnect a new bucket.
            String host = inet.getAddress() == null ? inet.getHostString() : inet.getAddress().getHostAddress();
            if (host != null && !host.isBlank()) remote = host;
        } else if (remoteAddress != null) {
            remote = remoteAddress.toString();
        }
        if ("remote".equalsIgnoreCase(mode)) return remote;
        // auto is the safe default. "header" remains an alias for older configs, but
        // address forwarding comes from a trusted Forge integration, not an HTTP header.
        java.net.InetAddress address = ClientIpResolveEvent.resolve(p);
        return address == null ? remote : address.getHostAddress();
    }

    private boolean persist() {
        try {
            store.save();
            return true;
        } catch (IOException e) {
            LOGGER.error("[yzt_super] users.json 写入失败，保留旧文件", e);
            return false;
        }
    }

    private void sendGatePrompt(ServerPlayer p) {
        WelcomeSync.promptGate(p);
    }

    private void sendLoginPrompt(ServerPlayer p) {
        WelcomeSync.promptLogin(p, this);
    }
}
