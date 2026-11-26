package com.finpilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpilot.assistant.TemplateSqlGenerator;
import com.finpilot.security.SecurityPrimitivesTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Each real provider is exercised against a local mock HTTP server: request shape + response parsing. */
class LlmProvidersTest {
    private MockWebServer server;
    private final ObjectMapper json = new ObjectMapper();
    private static final LlmRequest REQ = new LlmRequest(LlmTask.TEXT_TO_SQL, "sys", "Question: q",
            Map.of("question", "q"), 100);

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private String url() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private void respond(String body) {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test
    void anthropicSdkProvider() throws Exception {
        respond("{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"SELECT 1 FROM nl_goals\"}],\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}");
        AnthropicLlmProvider p = new AnthropicLlmProvider("sk-test", null, url(), Duration.ofSeconds(5));
        assertThat(p.name()).isEqualTo("anthropic:claude-opus-5-5");
        assertThat(p.complete(REQ)).isEqualTo("SELECT 1 FROM nl_goals");
        RecordedRequest r = server.takeRequest();
        assertThat(r.getPath()).isEqualTo("/v1/messages");
        assertThat(r.getHeaders().toString()).contains("sk-test");
        JsonNode body = json.readTree(r.getBody().readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(100);
        assertThat(body.path("system").toString()).contains("sys");
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("user");
    }

    @Test
    void anthropicErrorsBecomeLlmException() {
        server.enqueue(new MockResponse().setResponseCode(400).setHeader("Content-Type", "application/json")
                .setBody("{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}"));
        AnthropicLlmProvider p = new AnthropicLlmProvider("sk-test", "claude-opus-5-5", url(), Duration.ofSeconds(5));
        assertThatThrownBy(() -> p.complete(REQ)).isInstanceOf(LlmException.class);
    }

    @Test
    void openAi() throws Exception {
        respond("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\" SELECT 2 \"}}]}");
        var p = new OpenAiLlmProvider(RestClient.builder(), "sk-o", null, url(), Duration.ofSeconds(5));
        assertThat(p.complete(REQ)).isEqualTo("SELECT 2");
        RecordedRequest r = server.takeRequest();
        assertThat(r.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(r.getHeader("Authorization")).isEqualTo("Bearer sk-o");
        assertThat(json.readTree(r.getBody().readUtf8()).path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(p.name()).isEqualTo("openai:gpt-4o-mini");
    }

    @Test
    void gemini() throws Exception {
        respond("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"SELECT 3\"}]}}]}");
        var p = new GeminiLlmProvider(RestClient.builder(), "g-key", "gemini-x", url(), Duration.ofSeconds(5));
        assertThat(p.complete(REQ)).isEqualTo("SELECT 3");
        RecordedRequest r = server.takeRequest();
        assertThat(r.getPath()).isEqualTo("/v1beta/models/gemini-x:generateContent");
        assertThat(r.getHeader("x-goog-api-key")).isEqualTo("g-key");
    }

    @Test
    void ollamaAndEmptyAndHttpErrors() throws Exception {
        respond("{\"message\":{\"content\":\"SELECT 4\"}}");
        var p = new OllamaLlmProvider(RestClient.builder(), null, url(), Duration.ofSeconds(5));
        assertThat(p.complete(REQ)).isEqualTo("SELECT 4");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/chat");
        respond("{\"message\":{\"content\":\"\"}}");
        assertThatThrownBy(() -> p.complete(REQ)).isInstanceOf(LlmException.class).hasMessageContaining("empty");
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> p.complete(REQ)).isInstanceOf(LlmException.class);
    }

    @Test
    void fakeProviderAndConfigSelection() {
        var props = SecurityPrimitivesTest.props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        var templates = new TemplateSqlGenerator(Clock.systemUTC(), props);
        LlmProvider fake = new LlmConfig().llmProvider(props, RestClient.builder(), templates);
        assertThat(fake.name()).isEqualTo("fake");
        assertThat(fake.complete(new LlmRequest(LlmTask.TEXT_TO_SQL, "", "", Map.of("question", "show my goals"), 10)))
                .contains("nl_goals");
        String advice = fake.complete(new LlmRequest(LlmTask.SAVINGS_ADVICE, "", "", Map.of("goalName", "Trip",
                "requiredMonthly", new BigDecimal("12000"), "averageMonthlySurplus", new BigDecimal("5000"),
                "monthsRemaining", 4, "topCutCategory", "Food", "topCutSaving", new BigDecimal("3000")), 10));
        assertThat(advice).contains("Trip").contains("Rs 12,000").contains("Food").contains("gap");
        String ok = fake.complete(new LlmRequest(LlmTask.SAVINGS_ADVICE, "", "", Map.of("goalName", "Trip",
                "requiredMonthly", 100, "averageMonthlySurplus", 5000, "monthsRemaining", 1), 10));
        assertThat(ok).contains("covers this").contains("1 month,");
        assertThatThrownBy(() -> new LlmConfig().llmProvider(withProvider(props, "anthropic", null),
                RestClient.builder(), templates)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LlmConfig().llmProvider(withProvider(props, "nope", "k"),
                RestClient.builder(), templates)).isInstanceOf(IllegalStateException.class);
        for (String name : new String[] {"anthropic", "openai", "gemini", "ollama"}) {
            assertThat(new LlmConfig().llmProvider(withProvider(props, name, "k"), RestClient.builder(), templates)
                    .name()).startsWith(name);
        }
    }

    private static com.finpilot.config.AppProperties withProvider(com.finpilot.config.AppProperties p, String provider,
                                                                  String key) {
        return new com.finpilot.config.AppProperties(p.timezone(), p.jwt(), p.encryption(), p.ml(),
                new com.finpilot.config.AppProperties.Ai(provider, null, key, null, Duration.ofSeconds(5)),
                p.readonlyDb(), p.rateLimit(), p.cors(), p.retrain(), p.seed());
    }
}
