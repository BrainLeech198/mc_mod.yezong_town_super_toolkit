package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** 进服/过闸后的提示：屏幕标题 + 聊天栏可点击按钮。 */
public final class WelcomeSync {
    private WelcomeSync() {}

    /** 已注册→引导登录；未注册→引导注册（按钮自动填入对应命令）。 */
    public static void promptLogin(ServerPlayer p, AuthManager auth) {
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        boolean registered = auth != null && auth.accountOf(name) != null;
        Component sub = Lang.msg(registered ? "prompt_login.sub" : "prompt_register.sub");
        TitleSink.send(p, Lang.msg("prompt_login.title"), sub);
        ChatPrompt.send(p, sub, registered ? "/yztauth login " : "/yztauth register ");
    }

    public static void promptGate(ServerPlayer p) {
        Component sub = Lang.msg("prompt_gate.sub");
        TitleSink.send(p, Lang.msg("prompt_gate.title"), sub);
        ChatPrompt.send(p, sub, "/yztauth gate ");
    }

    public static void clearTitles(ServerPlayer p) {
        TitleSink.send(p, Component.empty(), Component.empty());
    }
}
