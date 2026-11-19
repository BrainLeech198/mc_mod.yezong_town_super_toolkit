package com.yztc.auth.auth;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingState {
    public static final Map<UUID, PendingState> ALL = new ConcurrentHashMap<>();

    public final ServerPlayer player;
    public final double originX, originY, originZ;
    public final float originYaw, originPitch;
    public final long joinedAt;
    public int loginFails = 0;
    public boolean gatePassed = false;
    public boolean disconnecting;
    /** 上次重发标题的时刻，用于闪烁节流。 */
    public long lastTitleMs;

    public PendingState(ServerPlayer player) {
        this.player = player;
        this.originX = player.getX();
        this.originY = player.getY();
        this.originZ = player.getZ();
        this.originYaw = player.getYRot();
        this.originPitch = player.getXRot();
        this.joinedAt = System.currentTimeMillis();
        this.lastTitleMs = this.joinedAt;
    }
}
