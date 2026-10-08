package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.PendingState;
import com.yztc.auth.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** 进服/过闸/登录后的提示：屏幕标题（金）+ 副标题（黄）+ 聊天栏按钮（绿）。 */
public final class WelcomeSync {
    private WelcomeSync() {}

    // ---- 纯构建器（可单测，不触网） ----

    /** 标题：金色加粗。gate=true 为闸门标题，否则为登录标题。 */
    public static Component title(boolean gate) {
        return Lang.msg(gate ? "prompt_gate.title" : "prompt_login.title")
                .copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** 副标题（登录/注册路径）：黄色。registered=true 引导登录，否则引导注册。 */
    public static Component sub(boolean registered) {
        return Lang.msg(registered ? "prompt_login.sub" : "prompt_register.sub")
                .copy().withStyle(ChatFormatting.YELLOW);
    }

    /** 副标题（闸门路径）：黄色。 */
    public static Component gateSub() {
        return Lang.msg("prompt_gate.sub").copy().withStyle(ChatFormatting.YELLOW);
    }

    /** 聊天栏按钮文本键：与副标题分开，避免把副标题错当聊天栏内容。 */
    public static String buttonKey(boolean gate, boolean registered) {
        if (gate) return "prompt_gate.button";
        return registered ? "prompt_login.button" : "prompt_register.button";
    }

    /** 按钮点击要建议的命令（只填入输入框，由玩家确认）。 */
    public static String commandFor(boolean gate, boolean registered) {
        if (gate) return "/yztauth gate ";
        return registered ? "/yztauth login " : "/yztauth register ";
    }

    /** 聊天栏按钮：绿色下划线可点击。 */
    public static Component button(boolean gate, boolean registered) {
        return ChatPrompt.button(Lang.msg(buttonKey(gate, registered)), commandFor(gate, registered));
    }

    /** 欢迎标题：金色加粗。 */
    public static Component welcomeTitle() {
        return Lang.msg("welcome.title").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** 欢迎副标题：黄色，含玩家名。 */
    public static Component welcomeSub(String name) {
        return Lang.msg("welcome.sub", name).copy().withStyle(ChatFormatting.YELLOW);
    }

    // ---- 发送器 ----

    /** 已注册→引导登录；未注册→引导注册（按钮自动填入对应命令）。 */
    public static void promptLogin(ServerPlayer p, AuthManager auth) {
        boolean registered = isRegistered(p, auth);
        TitleSink.send(p, title(false), sub(registered));
        ChatPrompt.send(p, Lang.msg(buttonKey(false, registered)), commandFor(false, registered));
    }

    public static void promptGate(ServerPlayer p) {
        TitleSink.send(p, title(true), gateSub());
        ChatPrompt.send(p, Lang.msg(buttonKey(true, false)), commandFor(true, false));
    }

    /** 登录/注册成功后：欢迎语（金标题 + 黄副标题）。 */
    public static void welcome(ServerPlayer p) {
        TitleSink.send(p, welcomeTitle(), welcomeSub(p.getGameProfile().getName()));
    }

    /** 闪烁重发：只重发标题/副标题，不刷聊天栏，避免刷屏。 */
    public static void resendTitle(ServerPlayer p, AuthManager auth) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null) return;
        if (!st.gatePassed) {
            TitleSink.send(p, title(true), gateSub());
        } else {
            boolean registered = isRegistered(p, auth);
            TitleSink.send(p, title(false), sub(registered));
        }
    }

    public static void clearTitles(ServerPlayer p) {
        TitleSink.send(p, Component.empty(), Component.empty());
    }

    private static boolean isRegistered(ServerPlayer p, AuthManager auth) {
        if (auth == null) return false;
        String name = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
        return auth.accountOf(name) != null;
    }
}
