package com.finpilot.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/** Single-node fallback limiter (tests / no Redis). */
public class InMemoryRateLimiter implements RateLimiter {
    private record Window(long startMs, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision hit(String key, int limit, Duration window) {
        long now = clock.millis();
        Window w = windows.compute(key, (k, cur) -> cur == null || now - cur.startMs() >= window.toMillis()
                ? new Window(now, 1) : new Window(cur.startMs(), cur.count() + 1));
        if (w.count() <= limit) {
            return new Decision(true, 0);
        }
        long retry = (w.startMs() + window.toMillis() - now + 999) / 1000;
        return new Decision(false, Math.max(1, retry));
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }
}
