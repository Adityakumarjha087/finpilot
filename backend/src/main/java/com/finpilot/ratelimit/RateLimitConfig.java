package com.finpilot.ratelimit;

import com.finpilot.config.AppProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {
    @Bean
    RateLimiter rateLimiter(AppProperties props, StringRedisTemplate redis, Clock clock) {
        return "memory".equalsIgnoreCase(props.rateLimit().backend())
                ? new InMemoryRateLimiter(clock) : new RedisRateLimiter(redis);
    }
}
