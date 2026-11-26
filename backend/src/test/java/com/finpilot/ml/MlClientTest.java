package com.finpilot.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpilot.config.AppProperties;
import com.finpilot.security.SecurityPrimitivesTest;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MlClientTest {
    private MockWebServer server;
    private MlClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        AppProperties p = SecurityPrimitivesTest.props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        AppProperties props = new AppProperties(p.timezone(), p.jwt(), p.encryption(),
                new AppProperties.Ml(server.url("/").toString(), "secret-token", Duration.ofSeconds(3)), p.ai(),
                p.readonlyDb(), p.rateLimit(), p.cors(), p.retrain(), p.seed());
        client = new MlClient(RestClient.builder(), props);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private void json(String body) {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test
    void callsEndpointsWithInternalToken() throws Exception {
        json("{\"model_version\":3,\"predictions\":[{\"id\":null,\"category\":\"Food\",\"confidence\":0.9,\"alternatives\":[]}]}");
        var r = client.categorize(new MlDtos.CategorizeRequest(List.of(new MlDtos.CategorizeItem(null, "SWIGGY", -1))));
        assertThat(r.modelVersion()).isEqualTo(3);
        assertThat(r.predictions().getFirst().category()).isEqualTo("Food");
        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/v1/categorize");
        assertThat(req.getHeader("X-Internal-Token")).isEqualTo("secret-token");

        json("{\"version\":2,\"trained_at\":\"t\",\"n_samples\":10,\"n_corrections\":1,\"holdout_accuracy\":0.9,"
                + "\"holdout_macro_f1\":0.8,\"classes\":[\"Food\"]}");
        assertThat(client.train(new MlDtos.TrainRequest(List.of())).holdoutAccuracy()).isEqualTo(0.9);
        json("{\"version\":2,\"trained_at\":\"t\",\"n_samples\":10,\"n_corrections\":1,\"holdout_accuracy\":0.9,"
                + "\"holdout_macro_f1\":0.8,\"classes\":[]}");
        assertThat(client.modelInfo().nSamples()).isEqualTo(10);
        json("{\"horizon\":1,\"confidence\":0.8,\"forecasts\":[]}");
        assertThat(client.forecast(new MlDtos.ForecastRequest(List.of(), 1, 0.8)).horizon()).isEqualTo(1);
        json("{\"scanned\":0,\"anomalies\":[]}");
        assertThat(client.anomalies(new MlDtos.AnomalyRequest(List.of())).scanned()).isZero();
        json("[{\"posted_at\":\"2026-01-01T00:00:00\",\"description\":\"x\",\"amount\":-1}]");
        assertThat(client.syntheticTransactions(1, 2)).hasSize(1);
    }

    @Test
    void failuresBecomeServiceUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> client.modelInfo()).isInstanceOf(MlUnavailableException.class);
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json"));
        assertThatThrownBy(() -> client.modelInfo()).isInstanceOf(MlUnavailableException.class);
    }
}
