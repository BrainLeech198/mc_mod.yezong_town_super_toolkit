package com.yztc.auth.auth;

import net.minecraft.server.level.ServerPlayer;

public final class HeaderProbe {
    private HeaderProbe() {}

    /** 1.20.1 的 ServerPlayer 无法直接读到 HTTP 头；无代理时返回 null。
     *  将来若接入能读取代理头的中转，在此处替换实现即可。 */
    public static String firstValue(ServerPlayer p, String headerName) {
        return null;
    }
}
