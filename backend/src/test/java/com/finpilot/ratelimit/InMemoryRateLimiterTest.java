package com.finpilot.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {
    @Test
    void fixedWindow() {
        AtomicLong now = new AtomicLong(0);
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        InMemoryRateLimiter rl = new InMemoryRateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            assertThat(rl.hit("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        var denied = rl.hit("k", 3, Duration.ofMinutes(1));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(60);
        now.set(61_000);
        assertThat(rl.hit("k", 3, Duration.ofMinutes(1)).allowed()).isTrue();
        rl.hit("k", 1, Duration.ofMinutes(1));
        rl.reset("k");
        assertThat(rl.hit("k", 1, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
