package com.finpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** OpenAI Chat Completions API (also works with OpenAI-compatible gateways via LLM_BASE_URL). */
public class OpenAiLlmProvider extends HttpJsonLlmProvider {
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    private final String apiKey;

    public OpenAiLlmProvider(RestClient.Builder builder, String apiKey, String model, String baseUrl, Duration timeout) {
        super(builder, baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl,
                model == null || model.isBlank() ? DEFAULT_MODEL : model, timeout);
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "openai:" + model;
    }

    @Override
    protected JsonNode send(LlmRequest r) {
        Map<String, Object> body = Map.of("model", model, "max_tokens", r.maxTokens(), "messages", List.of(
                Map.of("role", "system", "content", r.system()), Map.of("role", "user", "content", r.prompt())));
        return http.post().uri("/v1/chat/completions").header("Authorization", "Bearer " + apiKey).body(body)
                .retrieve().body(JsonNode.class);
    }

    @Override
    protected String extract(JsonNode body) {
        return body == null ? null : body.path("choices").path(0).path("message").path("content").asText(null);
    }
}
