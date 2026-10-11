package com.yztc.auth.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天栏双线方框：把标题与若干内容行包进 {@code ╔═║╚}，框线金色、标题金色加粗。
 * 刻意不画右边框——聊天栏字体宽度不齐时右边线会参差，左侧一条竖线更干净。
 *
 * <p>宽度按列宽模型（见 {@link TextWidth}）计算：上下横线等长，内容行只留左竖线。
 * 内容行按原样嵌入（保留其内部样式与点击事件）。
 */
public final class ChatBox {
    private ChatBox() {}

    /** 纯逻辑：框内可用宽度（不含边框与两侧各一空格）。 */
    public static int innerWidth(String title, List<String> lines) {
        int w = TextWidth.displayWidth(title) + 1; // 顶部仍需「═ 标题 ═」至少留一格
        for (String l : lines) w = Math.max(w, TextWidth.displayWidth(l));
        return w;
    }

    /** 纯逻辑：整框的纯文本行（无上色），供自测校验。 */
    public static List<String> boxText(String title, List<String> lines) {
        int w = innerWidth(title, lines);
        int titleW = TextWidth.displayWidth(title);
        int fill = Math.max(1, w + 1 - (titleW + 2)); // 至少一格 ═，免得标题贴着行尾
        int topW = 4 + titleW + fill; // ╔═ + 空格 + 标题 + 空格 + ═*fill
        List<String> out = new ArrayList<>();
        out.add("╔═ " + title + " " + "═".repeat(fill));
        for (String l : lines) out.add("║ " + l);
        out.add("╚" + "═".repeat(topW - 1));
        return out;
    }

    /** 聊天组件版：标题 + 内容行 → 已套框的组件列表，可逐条 sendSystemMessage。 */
    public static List<Component> box(Component title, List<Component> lines) {
        String plainTitle = title.getString();
        List<String> plains = new ArrayList<>(lines.size());
        for (Component c : lines) plains.add(c.getString());
        int w = innerWidth(plainTitle, plains);
        int titleW = TextWidth.displayWidth(plainTitle);
        int fill = Math.max(1, w + 1 - (titleW + 2));
        int topW = 4 + titleW + fill;

        List<Component> out = new ArrayList<>();
        MutableComponent top = border("╔═ ");
        top.append(title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        top.append(border(" " + "═".repeat(fill)));
        out.add(top);

        for (Component c : lines) {
            MutableComponent row = border("║ ");
            row.append(c);
            out.add(row);
        }
        out.add(border("╚" + "═".repeat(topW - 1)));
        return out;
    }

    private static MutableComponent border(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }
}
