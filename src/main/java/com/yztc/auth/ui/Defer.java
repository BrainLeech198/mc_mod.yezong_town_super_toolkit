package com.yztc.auth.ui;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

/**
 * 把动作推迟到**下一个服务端 tick** 执行。
 *
 * <p>为什么不能直接用 {@code server.execute(...)}：{@code BlockableEventLoop} 的语义是
 * 「不在主线程、或正处在任务执行中」才排队，否则**同步立即执行**。而
 * {@code clicked()} / {@code removed()} 都在主线程的点击/关屏处理流程内部，
 * 此时同步再开一个菜单（箱子/铁砧）会与紧随其后的收尾逻辑（{@code handleContainerClick}
 * 的槽位回写、{@code resumeRemoteUpdates}）或关屏复位（{@code doCloseContainer}
 * 把 containerMenu 换回背包）撞车，客户端与服务端菜单当场错位，表现为「点了没反应」。
 *
 * <p>用 {@link TickTask} 显式排到下一 tick，保证新菜单是在当前点击/关屏流程**完全结束之后**
 * 才打开，从根上消除错位。
 */
public final class Defer {
    private Defer() {}

    /** 在下一服务端 tick 执行 {@code action}（若玩家已离线则忽略）。 */
    public static void nextTick(ServerPlayer p, Runnable action) {
        if (p == null || p.server == null) return;
        var server = p.server;
        server.tell(new TickTask(server.getTickCount() + 1, action));
    }
}
