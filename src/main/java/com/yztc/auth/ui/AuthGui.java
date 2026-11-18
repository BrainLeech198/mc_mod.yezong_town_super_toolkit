package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.GateManager;
import com.yztc.auth.command.RootCommand;
import com.yztc.auth.lang.Lang;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * 认证用铁砧输入框：进服/过闸后自动弹出；点顶部输入框输入，点右侧结果槽提交；Esc 关闭。
 *
 * <p>提交时复用 {@link RootCommand} 与 {@link AuthManager} 的认证逻辑，因此闸门校验、失败计数、审计、封锁放行等规则保持一致；
 * 失败时保留当前窗口，成功后关闭。
 */
public final class AuthGui {
    private AuthGui() {}

    /** 按当前状态选框：需过闸→闸门框；否则已注册→登录框，未注册→注册框。 */
    public static void openFor(ServerPlayer p, AuthManager auth) {
        if (auth != null && auth.gateRequired(p)) openGate(p, auth);
        else openLoginOrRegister(p, auth);
    }

    public static void openGate(ServerPlayer p, AuthManager auth) {
        AnvilInput.openFlow(p, WelcomeSync.anvilTitle(true, false), null, (player, password) -> {
            GateManager.GateResult result = RootCommand.attemptGate(player, auth, password);
            if (result == GateManager.GateResult.OK || result == GateManager.GateResult.NOT_SET) {
                return AnvilInput.Response.close(() -> {
                    WelcomeSync.promptLogin(player, auth);
                    openLoginOrRegister(player, auth);
                });
            }
            return AnvilInput.Response.retry(Lang.msg("auth_gate_retry"));
        }, () -> {});
    }

    public static void openLoginOrRegister(ServerPlayer p, AuthManager auth) {
        boolean registered = auth != null
                && auth.accountOf(p.getGameProfile().getName().toLowerCase(Locale.ROOT)) != null;
        AnvilInput.openFlow(p, WelcomeSync.anvilTitle(false, registered), null, (player, password) -> {
            boolean hasAccount = auth != null
                    && auth.accountOf(player.getGameProfile().getName().toLowerCase(Locale.ROOT)) != null;
            if (hasAccount) RootCommand.doLogin(player, auth, password);
            else RootCommand.doRegister(player, auth, password);
            return auth != null && auth.isAuthenticated(player)
                    ? AnvilInput.Response.close()
                    : AnvilInput.Response.retry(Lang.msg(hasAccount
                            ? "auth_login_retry" : "auth_register_retry"));
        }, () -> {});
    }
}
