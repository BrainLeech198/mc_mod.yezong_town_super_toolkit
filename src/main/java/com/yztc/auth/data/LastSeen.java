package com.yztc.auth.data;

/** 玩家最后一次下线的位置与游戏模式快照。 */
public final class LastSeen {
    public String name;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public long at;
    /** survival / creative / adventure / spectator；旧数据缺失时为 null。 */
    public String gameMode;
}
