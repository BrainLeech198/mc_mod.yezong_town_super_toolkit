package com.yztc.auth.ui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 玩家菜单动作在下一次服务端 tick 执行，避开本次关窗/槽位回写。所有调用都在服务端主线程。 */
public final class Defer {
    private record Action(ServerPlayer owner, AbstractContainerMenu menu, int tick, Runnable callback) {}

    private static final Map<UUID, ArrayDeque<Action>> pending = new HashMap<>();

    private Defer() {}

    public static void nextTick(ServerPlayer player, Runnable action) {
        if (player == null || player.hasDisconnected()) return;
        pending.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>())
                .addLast(new Action(player, player.containerMenu, player.server.getTickCount() + 1, action));
    }

    public static void runDue(ServerPlayer player) {
        UUID id = player.getUUID();
        ArrayDeque<Action> queue = pending.get(id);
        if (queue == null) return;
        while (!queue.isEmpty() && queue.peekFirst().tick() <= player.server.getTickCount()) {
            Action next = queue.removeFirst();
            if (next.owner() == player && next.menu() == player.containerMenu
                    && !player.hasDisconnected() && player.server.getPlayerList().getPlayer(id) == player) {
                next.callback().run();
            }
        }
        if (queue.isEmpty()) pending.remove(id, queue);
    }

    public static void forget(ServerPlayer player) {
        ArrayDeque<Action> queue = pending.remove(player.getUUID());
        if (queue != null) queue.clear();
    }
}
