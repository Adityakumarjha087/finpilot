package com.finpilot.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpilot.anomaly.AnomalyService;
import com.finpilot.auth.AuthService;
import com.finpilot.budget.BudgetRepository;
import com.finpilot.goal.GoalRepository;
import com.finpilot.importing.ImportService;
import com.finpilot.ratelimit.RateLimiter;
import com.finpilot.ratelimit.RedisRateLimiter;
import com.finpilot.seed.DemoDataSeeder;
import com.finpilot.support.IntegrationTest;
import com.finpilot.transaction.TransactionRepository;
import com.finpilot.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class SeederAndRateLimiterIntegrationTest extends IntegrationTest {
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ImportService imports;
    @Autowired BudgetRepository budgets;
    @Autowired GoalRepository goals;
    @Autowired AnomalyService anomalies;
    @Autowired TransactionRepository transactions;
    @Autowired StringRedisTemplate redis;
    @Autowired RateLimiter limiter;

    @Test
    void seederCreatesDemoDataOnceThroughTheImportPipeline() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now().minusMonths(3);
        for (int i = 0; i < 60; i++) {
            rows.add(Map.of("posted_at", start.plusDays(i).withNano(0).toString(),
                    "description", "UPI/SWIGGY/" + (100000 + i) + "/Payment", "amount", -250.0 - i, "category", "Food"));
        }
        Mockito.when(ml.syntheticTransactions(12, 42)).thenReturn(rows);
        DemoDataSeeder seeder = new DemoDataSeeder(users, auth, ml, imports, budgets, goals, anomalies,
                Clock.systemUTC(), ZoneId.of("Asia/Kolkata"));
        if (users.existsByEmailIgnoreCase(DemoDataSeeder.DEMO_EMAIL)) {
            return; // already seeded by an earlier run in this JVM
        }
        seeder.run(null);
        var demo = users.findByEmailIgnoreCase(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        assertThat(transactions.countByUserId(demo.getId())).isEqualTo(60);
        assertThat(budgets.findByUserIdOrderByCategoryAsc(demo.getId())).hasSize(5);
        assertThat(goals.findByUserIdOrderByTargetDateAsc(demo.getId())).hasSize(2);
        assertThat(users.findByEmailIgnoreCase(DemoDataSeeder.ADMIN_EMAIL)).isPresent();
        seeder.run(null); // idempotent
        assertThat(transactions.countByUserId(demo.getId())).isEqualTo(60);
    }

    @Test
    void redisFixedWindowLimiter() {
        assertThat(limiter).isInstanceOf(RedisRateLimiter.class);
        String key = "test:" + UUID.randomUUID();
        assertThat(limiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isTrue();
        assertThat(limiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isTrue();
        var denied = limiter.hit(key, 2, Duration.ofSeconds(30));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 30L);
        limiter.reset(key);
        assertThat(limiter.hit(key, 2, Duration.ofSeconds(30)).allowed()).isTrue();
        assertThat(redis.hasKey("rl:" + key)).isTrue();
    }

    @Test
    void redisLimiterFailsOpen() {
        StringRedisTemplate broken = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(broken.execute(Mockito.any(org.springframework.data.redis.core.script.RedisScript.class),
                Mockito.anyList(), Mockito.any(Object[].class))).thenThrow(new RuntimeException("down"));
        Mockito.when(broken.delete(Mockito.anyString())).thenThrow(new RuntimeException("down"));
        RedisRateLimiter rl = new RedisRateLimiter(broken);
        assertThat(rl.hit("x", 1, Duration.ofSeconds(1)).allowed()).isTrue();
        rl.reset("x");
    }
}
