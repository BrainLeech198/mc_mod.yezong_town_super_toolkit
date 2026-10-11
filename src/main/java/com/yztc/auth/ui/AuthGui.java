package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * 认证用铁砧输入框：进服/过闸后自动弹出，点左侧占位纸后在名字框输入，**输完按 Esc 即提交**；关掉后仍可点聊天栏按钮重开。
 *
 * <p>提交时不复制任何认证逻辑——直接把输入当命令参数执行（{@code /yzts gate|login|register <密码>}），
 * 因此闸门校验、失败计数、审计、封锁放行等既有规则原样生效。
 */
public final class AuthGui {
    private AuthGui() {}

    /** 按当前状态选框：需过闸→闸门框；否则已注册→登录框，未注册→注册框。 */
    public static void openFor(ServerPlayer p, AuthManager auth) {
        if (auth != null && auth.gateRequired(p)) openGate(p);
        else openLoginOrRegister(p, auth);
    }

    public static void openGate(ServerPlayer p) {
        open(p, WelcomeSync.anvilTitle(true), "/yzts gate ");
    }

    public static void openLoginOrRegister(ServerPlayer p, AuthManager auth) {
        boolean registered = auth != null
                && auth.accountOf(p.getGameProfile().getName().toLowerCase(Locale.ROOT)) != null;
        open(p, WelcomeSync.anvilTitle(false), registered ? "/yzts login " : "/yzts register ");
    }

    private static void open(ServerPlayer p, Component title, String cmdPrefix) {
        AnvilInput.open(p, title,
                UiSession.open(p.getUUID(), pw -> run(p, cmdPrefix + pw), () -> { }));
    }

    private static void run(ServerPlayer p, String command) {
        if (p.server == null) return;
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), command);
    }
}
