package com.yztc.auth.ui;

import com.yztc.auth.lang.Lang;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 双入口二选一：铁砧输入（快，但不能有空格）/ 命令输入（可含空格）。
 * 像 {@link LogoutPrompt} 一样先在聊天栏给两个按钮，而不是直接弹铁砧。
 */
public final class InputPrompt {
    private InputPrompt() {}

    private static final Map<String, Runnable> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();

    /** 注册待执行的铁砧打开动作，返回一次性令牌。 */
    public static String register(Runnable action) {
        String token = Integer.toHexString(SEQ.incrementAndGet());
        PENDING.put(token, action);
        return token;
    }

    /** 触发并移除；未注册或已用过返回 false。 */
    public static boolean fire(String token) {
        Runnable r = PENDING.remove(token);
        if (r == null) return false;
        r.run();
        return true;
    }

    /**
     * 发送二选一提示。
     *
     * @param promptTitle 选择框标题
     * @param anvilTitle  铁砧输入框标题
     * @param onAnvil     铁砧提交回调
     * @param cmdSuggest  点「命令输入」后填入聊天栏的命令前缀（玩家补全内容后发送）
     */
    public static void choose(ServerPlayer p, Component promptTitle, Component anvilTitle,
                              Consumer<String> onAnvil, String cmdSuggest) {
        String token = register(() -> AnvilInput.open(p, anvilTitle,
                UiSession.open(p.getUUID(), onAnvil, () -> {})));
        Component buttons = Component.empty()
                .append(ChatPrompt.runButton(Lang.msg("btn_anvil"), "/yzts input " + token))
                .append(Component.literal("   "))
                .append(ChatPrompt.button(Lang.msg("btn_cmd"), cmdSuggest));
        for (Component c : ChatBox.box(promptTitle, List.of(buttons))) {
            p.sendSystemMessage(c);
        }
    }
}
