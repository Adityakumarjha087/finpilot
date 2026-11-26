package com.finpilot.ratelimit;

import java.time.Duration;

/** Fixed-window rate limiter. */
public interface RateLimiter {
    /** Records a hit for {@code key}; returns the decision for this hit. */
    Decision hit(String key, int limit, Duration window);

    /** Clears a key (e.g. after a successful login). */
    void reset(String key);

    record Decision(boolean allowed, long retryAfterSeconds) {}
}
