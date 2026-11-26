package com.finpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** Google Gemini generateContent REST API. */
public class GeminiLlmProvider extends HttpJsonLlmProvider {
    public static final String DEFAULT_MODEL = "gemini-2.0-flash";
    private final String apiKey;

    public GeminiLlmProvider(RestClient.Builder builder, String apiKey, String model, String baseUrl, Duration timeout) {
        super(builder, baseUrl == null || baseUrl.isBlank() ? "https://generativelanguage.googleapis.com" : baseUrl,
                model == null || model.isBlank() ? DEFAULT_MODEL : model, timeout);
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "gemini:" + model;
    }

    @Override
    protected JsonNode send(LlmRequest r) {
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", r.system()))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", r.prompt())))),
                "generationConfig", Map.of("maxOutputTokens", r.maxTokens(), "temperature", 0));
        return http.post().uri("/v1beta/models/{model}:generateContent", model).header("x-goog-api-key", apiKey)
                .body(body).retrieve().body(JsonNode.class);
    }

    @Override
    protected String extract(JsonNode body) {
        return body == null ? null
                : body.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
    }
}
