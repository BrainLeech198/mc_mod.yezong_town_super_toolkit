package com.yztc.auth.broadcast;

import com.yztc.auth.lang.Lang;
import com.yztc.auth.ui.Sounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂机公告：玩家超过阈值无操作（位置/朝向不动）时，全服广播一条随机「睡着了」文案；
 * 重新有操作时，再全服广播随机「回来了」文案，并给本人播放提示音 + 发送私人欢迎语。
 *
 * <p>与 {@code AuthManager} 的 AFK 自动登出相互独立：本类只播报、状态仅存内存、阈值更短（默认 180s）。
 * 活动信号由 {@code AuthManager.onAuthenticatedTick} 复用，不重复判定。
 */
public final class AfkBroadcast {
    private AfkBroadcast() {}

    /** 离开 / 返回文案变体数，与语言键 afk_away_N / afk_back_N 一一对应（N 从 1 起）。 */
    public static final int AWAY_VARIANTS = 6;
    public static final int BACK_VARIANTS = 6;

    /** 当前被标记为「挂机中」的玩家（仅内存，退服/登出即清）。 */
    private static final Set<UUID> AWAY = ConcurrentHashMap.newKeySet();
    private static final Random RND = new Random();

    // ---- 纯逻辑 ----

    /** 纯逻辑：尚未标记挂机、且空闲已达阈值 → 应广播「离开了」。 */
    public static boolean shouldEnterAway(boolean away, long idleMs, long limitMs) {
        return !away && idleMs >= limitMs;
    }

    /** 纯逻辑：已标记挂机、且此刻有操作 → 应广播「回来了」。 */
    public static boolean shouldReturn(boolean away, boolean active) {
        return away && active;
    }

    /** 纯逻辑：随机取一个 0-based 变体序号；数量非正时退回 0（不抛异常）。 */
    public static int pickVariant(int size, Random r) {
        return size <= 0 ? 0 : r.nextInt(size);
    }

    /** 语言键：第 idx（0-based）个离开文案。 */
    public static String awayKey(int idx) { return "afk_away_" + (idx + 1); }

    /** 语言键：第 idx（0-based）个返回文案。 */
    public static String backKey(int idx) { return "afk_back_" + (idx + 1); }

    // ---- 消息构建（可单测，不触网） ----

    /** 离开：[金色加粗玩家名] + 空格 + [灰色斜体随机文案]。 */
    public static MutableComponent awayMessage(String name, int idx) {
        return Component.literal(name == null ? "" : name)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Lang.msg(awayKey(idx)).copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** 返回：[绿色加粗玩家名] + 空格 + [黄色随机文案]。 */
    public static MutableComponent backMessage(String name, int idx) {
        return Component.literal(name == null ? "" : name)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Lang.msg(backKey(idx)).copy().withStyle(ChatFormatting.YELLOW));
    }

    /** 私人欢迎语（绿色）。 */
    public static MutableComponent welcomeMessage() {
        return Lang.msg("afk_welcome").copy().withStyle(ChatFormatting.GREEN);
    }

    // ---- 状态与发送 ----

    /** 每 tick 调用（仅已认证玩家）：空闲越阈值→播报离开；已挂机后有操作→播报返回。 */
    public static void tick(ServerPlayer p, boolean active, long idleMs, long limitMs, boolean enabled) {
        if (!enabled || p == null || p.server == null) return;
        UUID id = p.getUUID();
        boolean away = AWAY.contains(id);
        if (shouldEnterAway(away, idleMs, limitMs)) {
            AWAY.add(id);
            broadcast(p.server, awayMessage(p.getGameProfile().getName(), pickVariant(AWAY_VARIANTS, RND)));
        } else if (shouldReturn(away, active)) {
            AWAY.remove(id);
            broadcast(p.server, backMessage(p.getGameProfile().getName(), pickVariant(BACK_VARIANTS, RND)));
            Sounds.welcomeBack(p);
            p.sendSystemMessage(welcomeMessage());
        }
    }

    /** 离开/登出/重新认证时清除标记，避免下次进服误报「回来了」。 */
    public static void forget(UUID id) {
        if (id != null) AWAY.remove(id);
    }

    /** 全清（配置重载等）。 */
    public static void clearAll() { AWAY.clear(); }

    private static void broadcast(MinecraftServer server, Component line) {
        if (server == null || line == null) return;
        server.getPlayerList().broadcastSystemMessage(line, false);
    }
}
