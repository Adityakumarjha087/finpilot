package com.finpilot.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpilot.ml.MlClient;
import com.finpilot.ml.MlDtos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests: real PostgreSQL 16 + Redis 7 via Testcontainers (shared, started once per JVM),
 * real Flyway migrations incl. the read-only assistant role, and a scripted stand-in for the Python ML service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finpilot").withUsername("finpilot").withPassword("test");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("finpilot.readonly-db.url", POSTGRES::getJdbcUrl);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @MockitoBean
    protected MlClient ml;

    @BeforeEach
    void scriptMl() {
        when(ml.categorize(any())).thenAnswer(inv -> {
            MlDtos.CategorizeRequest req = inv.getArgument(0);
            List<MlDtos.Prediction> preds = new ArrayList<>();
            for (MlDtos.CategorizeItem item : req.items()) {
                preds.add(new MlDtos.Prediction(item.id(), fakeCategory(item), 0.91, List.of()));
            }
            return new MlDtos.CategorizeResponse(1, preds);
        });
        when(ml.anomalies(any())).thenAnswer(inv -> {
            MlDtos.AnomalyRequest req = inv.getArgument(0);
            return req.transactions().stream().filter(t -> t.amount() < 0)
                    .min((a, b) -> Double.compare(a.amount(), b.amount()))
                    .map(t -> new MlDtos.AnomalyResponse(req.transactions().size(),
                            List.of(new MlDtos.AnomalyOut(t.id(), 0.97, List.of("Amount is 6.0x your typical spend")))))
                    .orElse(new MlDtos.AnomalyResponse(req.transactions().size(), List.of()));
        });
        when(ml.forecast(any())).thenAnswer(inv -> {
            MlDtos.ForecastRequest req = inv.getArgument(0);
            List<MlDtos.SeriesForecast> out = new ArrayList<>();
            for (MlDtos.Series s : req.series()) {
                double mean = s.history().stream().mapToDouble(MlDtos.SeriesPoint::amount).average().orElse(0);
                List<MlDtos.ForecastPoint> pts = new ArrayList<>();
                for (int i = 1; i <= req.horizon(); i++) {
                    pts.add(new MlDtos.ForecastPoint("2099-0" + i, mean, mean * 0.8, mean * 1.2));
                }
                out.add(new MlDtos.SeriesForecast(s.key(), "mean", pts));
            }
            return new MlDtos.ForecastResponse(req.horizon(), req.confidence(), out);
        });
        when(ml.train(any())).thenAnswer(inv -> {
            MlDtos.TrainRequest req = inv.getArgument(0);
            return new MlDtos.ModelInfo(2, "2026-11-01T00:00:00Z", 7000, req.corrections().size(), 0.98, 0.97,
                    List.of("Food"));
        });
        when(ml.modelInfo()).thenReturn(new MlDtos.ModelInfo(1, "2026-11-01T00:00:00Z", 7000, 0, 0.98, 0.97,
                List.of("Food")));
    }

    static String fakeCategory(MlDtos.CategorizeItem item) {
        String d = item.description().toUpperCase(Locale.ROOT);
        if (item.amount() > 0) {
            return "Income";
        }
        if (d.contains("SWIGGY") || d.contains("ZOMATO") || d.contains("CAFE") || d.contains("PIZZA")) {
            return "Food";
        }
        if (d.contains("NETFLIX") || d.contains("SPOTIFY")) {
            return "Subscriptions";
        }
        if (d.contains("UBER") || d.contains("OLA")) {
            return "Transport";
        }
        if (d.contains("RENT")) {
            return "Rent";
        }
        return "Shopping";
    }

    /** Registers a fresh user and returns its access token. */
    protected String registerAndLogin() throws Exception {
        return register("user-" + UUID.randomUUID() + "@example.com").path("accessToken").asText();
    }

    protected JsonNode register(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password1234\",\"displayName\":\"Test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
