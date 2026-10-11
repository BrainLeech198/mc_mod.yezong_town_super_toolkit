package com.yztc.auth.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * 聊天栏可点击按钮两种语义：
 * <ul>
 *   <li>{@link #button} — suggest_command：点击只把命令填进输入框，由玩家确认发送（如查看/指南）。</li>
 *   <li>{@link #runButton} — run_command：点击即执行（如重开铁砧 GUI、打开面板）。</li>
 * </ul>
 *
 * <p><b>务必保留 label 的原始组件、绝不在服务端 {@code getString()} 压平。</b>
 * 标签多为 {@code translatable-with-fallback}：压平发生在服务端，而专用服务器由 Forge 的
 * {@code LanguageHook} 预载了本 mod 的 {@code en_us}，只会解析成英文，既不是玩家语言、也不是
 * 服务端兜底语言。保留组件交给客户端解析，装了 mod 的客户端用自身语言、原版客户端回退到服务端兜底模板。
 */
public final class ChatPrompt {
    private ChatPrompt() {}

    /** 纯构造：给文本套上 {@code [ ]} 方括号 + 绿色下划线 + suggest_command 点击 + show_text 悬停（不改动原组件）。 */
    public static Component button(Component label, String commandToSuggest) {
        return bracket(label)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandToSuggest))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(commandToSuggest))));
    }

    /** 纯构造：{@code [ ]} 方括号 + 绿色下划线 + RUN_COMMAND 点击（点击即执行命令，用于打开 GUI）。 */
    public static Component runButton(Component label, String command) {
        return bracket(label)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    /** 用方括号包住 label，保留其原始子组件（可翻译、可点击），由客户端本地化渲染。 */
    private static MutableComponent bracket(Component label) {
        return Component.literal("[").append(label).append("]");
    }
}
