package com.yztc.auth.data;

/** 玩家最后一次下线的位置快照。 */
public final class LastSeen {
    public String name;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public long at;
}
