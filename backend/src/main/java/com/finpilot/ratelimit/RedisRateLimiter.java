package com.finpilot.ratelimit;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Atomic INCR + EXPIRE in a Lua script; fails open (allows) if Redis is unreachable. */
public class RedisRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c", Long.class);
    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Decision hit(String key, int limit, Duration window) {
        try {
            Long count = redis.execute(SCRIPT, List.of("rl:" + key), String.valueOf(window.toMillis()));
            if (count == null || count <= limit) {
                return new Decision(true, 0);
            }
            Long ttlMs = redis.getExpire("rl:" + key, java.util.concurrent.TimeUnit.MILLISECONDS);
            long ms = ttlMs == null || ttlMs < 0 ? window.toMillis() : ttlMs;
            return new Decision(false, Math.max(1, (ms + 999) / 1000));
        } catch (RuntimeException e) {
            log.warn("rate limiter unavailable, allowing request: {}", e.getMessage());
            return new Decision(true, 0);
        }
    }

    @Override
    public void reset(String key) {
        try {
            redis.delete("rl:" + key);
        } catch (RuntimeException e) {
            log.warn("rate limiter reset failed: {}", e.getMessage());
        }
    }
}
