package com.yztc.auth.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public final class RateLimiter {
    private final int maxCount;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public RateLimiter(int maxCount, long windowMillis) {
        this.maxCount = maxCount;
        this.windowMillis = windowMillis;
    }

    public synchronized boolean tryAcquire(String key, long now) {
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        evict(q, now);
        if (q.size() >= maxCount) return false;
        q.addLast(now);
        return true;
    }

    public synchronized long retryAfterMillis(String key, long now) {
        Deque<Long> q = hits.get(key);
        if (q == null) return 0;
        evict(q, now);
        if (q.size() < maxCount) return 0;
        return (q.peekFirst() + windowMillis) - now;
    }

    private void evict(Deque<Long> q, long now) {
        long cutoff = now - windowMillis;
        while (!q.isEmpty() && q.peekFirst() <= cutoff) q.pollFirst();
    }

    public synchronized void reset(String key) { hits.remove(key); }

    public synchronized int trackedKeys() { return hits.size(); }
}
