package com.yztc.auth.ui;

import com.yztc.auth.auth.AuthManager;
import com.yztc.auth.auth.PendingState;
import com.yztc.auth.config.AuthConfig;
import com.yztc.auth.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    /**
     * 铁砧菜单标题：铁砧窗口只有标题一处可写字，故把「怎么用」压成一行放这里，
     * 玩家一打开框就看到操作说明。不上色，贴近原版铁砧观感。
     */
    public static Component anvilTitle(boolean gate) {
        return Lang.msg(gate ? "prompt_gate.anvil" : "prompt_login.anvil");
    }

    /** 输入框标题直接说明当前目标：过闸、老玩家登录或新玩家设置密码。 */
    public static Component anvilTitle(boolean gate, boolean registered) {
        if (gate) return Lang.msg("prompt_gate.anvil");
        return registered
                ? Lang.msg("prompt_login.anvil")
                : Lang.msg("prompt_register.anvil", AuthConfig.minPasswordLength());
    }

    /** 副标题（登录/注册路径）：黄色。registered=true 引导登录，否则引导注册。 */
    public static MutableComponent sub(boolean registered) {
        Component prompt = registered
                ? Lang.msg("prompt_login.sub")
                : Lang.msg("prompt_register.sub", AuthConfig.minPasswordLength());
        return prompt.copy().withStyle(ChatFormatting.YELLOW);
    }

    /** 副标题（闸门路径）：黄色。 */
    public static MutableComponent gateSub() {
        return Lang.msg("prompt_gate.sub").copy().withStyle(ChatFormatting.YELLOW);
    }

    /** 聊天栏按钮文本键：与副标题分开，避免把副标题错当聊天栏内容。 */
    public static String buttonKey(boolean gate, boolean registered) {
        if (gate) return "prompt_gate.button";
        return registered ? "prompt_login.button" : "prompt_register.button";
    }

    /** 按钮点击要执行的命令（无参数=打开对应铁砧输入框，即「回到 GUI」）。 */
    public static String commandFor(boolean gate, boolean registered) {
        if (gate) return "/yzts gate";
        return registered ? "/yzts login" : "/yzts register";
    }

    /**
     * 聊天栏提示：黄色状态文字 + 一个绿色重开按钮。高级玩家仍可直接使用命令。
     */
    public static Component button(boolean gate, boolean registered) {
        String cmd = commandFor(gate, registered);
        MutableComponent line = Lang.msg(buttonKey(gate, registered)).copy().withStyle(ChatFormatting.YELLOW);
        line.append(ChatPrompt.runButton(Lang.msg("btn_gui"), cmd));
        return line;
    }

    /** 欢迎标题：金色加粗。 */
    public static Component welcomeTitle() {
        return Lang.msg("welcome.title").copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** 欢迎副标题：黄色，含玩家名。 */
    public static Component welcomeSub(String name) {
        return Lang.msg("welcome.sub", name).copy().withStyle(ChatFormatting.YELLOW);
    }

    /** 倒计时后缀（如「（剩余 123 秒）」），不上色，交由调用方拼接。 */
    public static Component countdownSuffix(long secs) {
        return Lang.msg("prompt_countdown", secs);
    }

    /** 把倒计时后缀接到主标题后（倒计时放主标题，避免与副标题引导语挤在一起）。 */
    public static MutableComponent titledWithCountdown(Component base, long secs) {
        return base.copy().append(countdownSuffix(secs));
    }

    /** 首次进服的全服广播文本。 */
    public static Component firstJoinMessage(String name) {
        return Lang.msg("first_join_broadcast", name);
    }

    /** 欢迎标题：空配置回退语言键，否则用自定义文字（仍为金色加粗）。 */
    public static Component resolveWelcomeTitle(String cfg) {
        if (cfg == null || cfg.isBlank()) return welcomeTitle();
        return Component.literal(cfg).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** 欢迎副标题：空配置回退语言键，否则用自定义文字（%s 替换为玩家名，仍为黄色）。 */
    public static Component resolveWelcomeSub(String cfg, String name) {
        if (cfg == null || cfg.isBlank()) return welcomeSub(name);
        return Component.literal(cfg.replace("%s", name)).withStyle(ChatFormatting.YELLOW);
    }

    // ---- 发送器 ----

    /** 已注册→引导登录；未注册→引导注册（按钮点击即重开对应铁砧 GUI）。 */
    public static void promptLogin(ServerPlayer p, AuthManager auth) {
        boolean registered = isRegistered(p, auth);
        TitleSink.send(p, title(false), sub(registered));
        p.sendSystemMessage(button(false, registered));
    }

    public static void promptGate(ServerPlayer p) {
        TitleSink.send(p, title(true), gateSub());
        p.sendSystemMessage(button(true, false));
    }

    /** 登录/注册成功后：欢迎语（金标题 + 黄副标题，可用配置覆盖文案）。停留约 3s 便于看清。 */
    public static void welcome(ServerPlayer p) {
        String name = p.getGameProfile().getName();
        TitleSink.send(p, resolveWelcomeTitle(AuthConfig.welcomeTitleText()),
                resolveWelcomeSub(AuthConfig.welcomeSubText(), name), 10, 60, 10);
    }

    /** 闪烁重发：只重发标题/副标题，不刷聊天栏，避免刷屏。未认证时副标题附倒计时。 */
    public static void resendTitle(ServerPlayer p, AuthManager auth, long remainSec) {
        PendingState st = PendingState.ALL.get(p.getUUID());
        if (st == null) return;
        if (!st.gatePassed) {
            TitleSink.send(p, titledWithCountdown(title(true), remainSec), gateSub());
        } else {
            boolean registered = isRegistered(p, auth);
            TitleSink.send(p, titledWithCountdown(title(false), remainSec), sub(registered));
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
