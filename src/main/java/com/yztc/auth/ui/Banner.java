package com.yztc.auth.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 开服启动横幅：中文双线框。
 *
 * <p>纯逻辑（宽度对齐、拼行、上色），便于自测；运行时版本号/作者由调用方从 mod 元数据读入。
 * 控制台用 ANSI 转义上色（Forge 的 JLine 控制台可显示；重定向到日志文件时颜色码会原样落盘），
 * 游戏内用 {@link ChatFormatting} 组件上色。
 */
public final class Banner {
    private static final char ESC = 27;
    public static final String GOLD = ESC + "[33m";
    public static final String GREEN = ESC + "[32m";
    public static final String WHITE = ESC + "[37m";
    public static final String RESET = ESC + "[0m";

    private static final int LABEL_W = 10;

    private Banner() {}

    /** 一行内容；accent=true 的行（状态行）用高亮色。 */
    public record Line(String text, boolean accent) {}

    /** 标签文案；由调用方从语言表读入（本类不内置任何语言）。 */
    public record Labels(String modTag, String version, String minecraft, String forge, String author, String status) {
    }

    // ---- 宽度（全角算 2 列）---- 委托给共享列宽工具

    public static int displayWidth(String s) {
        return TextWidth.displayWidth(s);
    }

    public static String padRight(String s, int width) {
        return TextWidth.padRight(s, width);
    }

    // ---- 信息行 ----

    public static List<Line> infoLines(String serverName, String modName, String version, String author,
                                       String minecraft, String forge, Labels labels) {
        List<Line> out = new ArrayList<>();
        String head = (serverName == null || serverName.isBlank())
                ? modName + " · " + labels.modTag()
                : serverName + " · " + modName;
        out.add(new Line(head, false));
        out.add(new Line(padRight(labels.version(), LABEL_W) + version, false));
        out.add(new Line(padRight(labels.minecraft(), LABEL_W) + minecraft, false));
        out.add(new Line(padRight(labels.forge(), LABEL_W) + forge, false));
        out.add(new Line(padRight(labels.author(), LABEL_W) + author, false));
        out.add(new Line(labels.status(), true));
        return out;
    }

    private static int innerWidth(List<Line> content) {
        int w = 0;
        for (Line l : content) w = Math.max(w, displayWidth(l.text()));
        return w;
    }

    // ---- 控制台（ANSI 上色） ----

    public static List<String> consoleLines(String serverName, String modName, String version, String author,
                                            String minecraft, String forge, Labels labels) {
        List<Line> content = infoLines(serverName, modName, version, author, minecraft, forge, labels);
        int w = innerWidth(content);
        List<String> out = new ArrayList<>();
        out.add(GOLD + "╔" + "═".repeat(w + 2) + "╗" + RESET);
        for (Line l : content) {
            String body = padRight(l.text(), w);
            out.add(GOLD + "║ " + (l.accent() ? GREEN : WHITE) + body + GOLD + " ║" + RESET);
        }
        out.add(GOLD + "╚" + "═".repeat(w + 2) + "╝" + RESET);
        return out;
    }

    // ---- 游戏内（ChatFormatting 上色） ----

    public static List<Component> chatLines(String serverName, String modName, String version, String author,
                                            String minecraft, String forge, Labels labels) {
        List<Line> content = infoLines(serverName, modName, version, author, minecraft, forge, labels);
        int w = innerWidth(content);
        List<Component> out = new ArrayList<>();
        out.add(border("╔" + "═".repeat(w + 2) + "╗"));
        for (Line l : content) {
            MutableComponent c = border("║ ");
            c.append(Component.literal(padRight(l.text(), w))
                    .withStyle(l.accent() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            c.append(border(" ║"));
            out.add(c);
        }
        out.add(border("╚" + "═".repeat(w + 2) + "╝"));
        return out;
    }

    private static MutableComponent border(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }
}
