package com.yztc.auth.ui;

/**
 * 聊天栏方框的列宽工具：按「列宽模型」测量文本宽度——CJK/假名/全角形式等算 2 列，其余算 1 列。
 *
 * <p>原版字体是比例字体，本模型只保证方框在自身模型里自洽、左右边框对齐到同一列；
 * 不追求逐像素对齐（那需要内嵌整套字体宽度表）。
 */
public final class TextWidth {
    private TextWidth() {}

    public static int displayWidth(String s) {
        if (s == null) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    public static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)   // Hangul Jamo
                || (cp >= 0x2E80 && cp <= 0xA4CF) // CJK 部首/假名/汉字/注音 等
                || (cp >= 0xAC00 && cp <= 0xD7A3) // Hangul 音节
                || (cp >= 0xF900 && cp <= 0xFAFF) // CJK 兼容汉字
                || (cp >= 0xFE30 && cp <= 0xFE4F) // CJK 兼容形式
                || (cp >= 0xFF00 && cp <= 0xFF60) // 全角形式
                || (cp >= 0xFFE0 && cp <= 0xFFE6);
    }

    public static String padRight(String s, int width) {
        if (s == null) s = "";
        int dw = displayWidth(s);
        if (dw >= width) return s;
        return s + " ".repeat(width - dw);
    }
}
