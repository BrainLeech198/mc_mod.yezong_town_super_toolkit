package com.yztc.auth.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 登出禁重连（disconnect-throttle）：记每个 UUID 的解禁时刻，到期即淘汰。 */
public final class LogoutThrottle {
    private final Map<UUID, Long> until = new HashMap<>();

    public synchronized void mark(UUID id, long nowMillis, long seconds) {
        until.put(id, nowMillis + seconds * 1000L);
    }

    /** 至 nowMillis 是否仍在禁重连期；到期条目顺手淘汰。 */
    public synchronized boolean blocked(UUID id, long nowMillis) {
        Long at = until.get(id);
        if (at == null) return false;
        if (nowMillis < at) return true;
        until.remove(id);
        return false;
    }

    public synchronized void clear() { until.clear(); }

    public synchronized int trackedKeys() { return until.size(); }
}
