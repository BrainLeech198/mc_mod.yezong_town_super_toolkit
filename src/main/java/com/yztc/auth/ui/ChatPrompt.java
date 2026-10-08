package com.yztc.auth.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/** 聊天栏可点击按钮：点击把命令填进输入框（suggest_command，不自动发送）。 */
public final class ChatPrompt {
    private ChatPrompt() {}

    /** 纯构造：给副本套上绿色下划线 + suggest_command 点击 + show_text 悬停（不改动原组件）。 */
    public static Component button(Component label, String commandToSuggest) {
        return label.copy()
                .withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandToSuggest))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(commandToSuggest))));
    }

    public static void send(ServerPlayer p, Component label, String commandToSuggest) {
        p.sendSystemMessage(button(label, commandToSuggest));
    }
}
