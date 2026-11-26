package com.finpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** Local models through Ollama's /api/chat endpoint. */
public class OllamaLlmProvider extends HttpJsonLlmProvider {
    public static final String DEFAULT_MODEL = "llama3.1";

    public OllamaLlmProvider(RestClient.Builder builder, String model, String baseUrl, Duration timeout) {
        super(builder, baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl,
                model == null || model.isBlank() ? DEFAULT_MODEL : model, timeout);
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }

    @Override
    protected JsonNode send(LlmRequest r) {
        Map<String, Object> body = Map.of("model", model, "stream", false,
                "options", Map.of("temperature", 0, "num_predict", r.maxTokens()),
                "messages", List.of(Map.of("role", "system", "content", r.system()),
                        Map.of("role", "user", "content", r.prompt())));
        return http.post().uri("/api/chat").body(body).retrieve().body(JsonNode.class);
    }

    @Override
    protected String extract(JsonNode body) {
        return body == null ? null : body.path("message").path("content").asText(null);
    }
}
