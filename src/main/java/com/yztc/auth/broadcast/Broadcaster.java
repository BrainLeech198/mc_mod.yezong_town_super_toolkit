package com.yztc.auth.broadcast;

import com.yztc.auth.ui.TitleSink;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 全服大喇叭：内容净化、冷却判定、分角色通道选择、发送。 */
public final class Broadcaster {
    private Broadcaster() {}

    /** 广播通道。 */
    public enum Channel { CHAT, TITLE, SUBTITLE, ACTIONBAR }

    /** 每位玩家上次喊话时刻（毫秒）；重启即清空，不落盘。 */
    private static final Map<UUID, Long> LAST_SHOUT = new ConcurrentHashMap<>();

    /** 纯逻辑：按角色与配置开关选通道——管理员走已开启通道；玩家固定仅聊天栏。 */
    public static Set<Channel> channelsFor(boolean admin, boolean chat, boolean title,
                                           boolean subtitle, boolean actionbar) {
        if (!admin) return EnumSet.of(Channel.CHAT);
        EnumSet<Channel> set = EnumSet.noneOf(Channel.class);
        if (chat) set.add(Channel.CHAT);
        if (title) set.add(Channel.TITLE);
        if (subtitle) set.add(Channel.SUBTITLE);
        if (actionbar) set.add(Channel.ACTIONBAR);
        return set;
    }

    /** 纯逻辑：冷却剩余秒数（向上取整）；已就绪返回 0；cooldown<=0 视为无冷却。 */
    public static long cooldownRemaining(long lastAt, long now, int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        long readyAt = lastAt + cooldownSeconds * 1000L;
        if (now >= readyAt) return 0;
        return (readyAt - now + 999) / 1000;
    }

    /** 纯逻辑：净化文本——允许颜色时把 `&x` 转成 `§x`；不允许时剥掉颜色码；去首尾空白；超长截断。 */
    public static String sanitize(String text, boolean allowColors, int maxLen) {
        if (text == null) return "";
        String s = allowColors ? toSection(text) : stripColors(text);
        s = s.trim();
        if (maxLen > 0 && s.length() > maxLen) s = s.substring(0, maxLen);
        return s;
    }

    /** 纯逻辑：把 `&x` 形式的颜色码转成 `§x`（原生的 `§x` 原样保留），便于 Component 渲染。 */
    public static String toSection(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && isCode(s.charAt(i + 1))) {
                b.append('§').append(s.charAt(i + 1));
                i++;
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    /** 纯逻辑：剥掉 `&x` / `§x` 形式的颜色与格式码（x 为 §0-9a-fk-or）。 */
    public static String stripColors(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < s.length() && isCode(s.charAt(i + 1))) { i++; continue; }
            b.append(c);
        }
        return b.toString();
    }

    private static boolean isCode(char c) {
        return "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(c) >= 0;
    }

    /** 纯逻辑：聊天行 `前缀 名字: 内容`（前缀/名字为空则各自省略）。 */
    public static Component line(String prefix, String name, String text) {
        StringBuilder b = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) b.append(prefix).append(' ');
        if (name != null && !name.isBlank()) b.append(name).append(": ");
        b.append(text == null ? "" : text);
        return Component.literal(b.toString());
    }

    /** 纯逻辑：标题/副标题两段——仅标题→标题=内容；仅副标题→副标题=内容；两者→标题=内容, 副标题=前缀。 */
    public static String[] titleParts(boolean titleOn, boolean subtitleOn, String text, String prefix) {
        String title = titleOn ? text : "";
        String subtitle = subtitleOn ? (titleOn ? (prefix == null ? "" : prefix) : text) : "";
        return new String[]{title, subtitle};
    }

    // ---- 冷却状态（内存） ----

    public static long remainingFor(UUID id, long now, boolean admin) {
        if (admin) return 0;
        Long last = LAST_SHOUT.get(id);
        return cooldownRemaining(last == null ? 0L : last, now,
                com.yztc.auth.config.AuthConfig.broadcastCooldownSeconds());
    }

    public static void markShout(UUID id, long now) {
        LAST_SHOUT.put(id, now);
    }

    // ---- 发送 ----

    /** 向全服发送；{@code name} 为 null 表示管理员公告（不显发言人）。 */
    public static void send(MinecraftServer server, Set<Channel> channels, String prefix, String name, String text) {
        if (server == null || channels == null || channels.isEmpty()) return;
        Component chat = line(prefix, name, text);
        if (channels.contains(Channel.CHAT)) {
            server.getPlayerList().broadcastSystemMessage(chat, false);
        }
        boolean wantTitle = channels.contains(Channel.TITLE) || channels.contains(Channel.SUBTITLE);
        String[] tp = titleParts(channels.contains(Channel.TITLE), channels.contains(Channel.SUBTITLE), text, prefix);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (wantTitle) TitleSink.send(p, Component.literal(tp[0]), Component.literal(tp[1]));
            if (channels.contains(Channel.ACTIONBAR)) p.sendSystemMessage(chat, true);
        }
    }
}
