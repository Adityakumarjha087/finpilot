package com.finpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Shared plumbing for providers reached over plain JSON HTTP (OpenAI, Gemini, Ollama). */
abstract class HttpJsonLlmProvider implements LlmProvider {
    protected final RestClient http;
    protected final String model;

    protected HttpJsonLlmProvider(RestClient.Builder builder, String baseUrl, String model, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(timeout);
        this.http = configure(builder.clone().baseUrl(baseUrl).requestFactory(factory)).build();
        this.model = model;
    }

    protected RestClient.Builder configure(RestClient.Builder builder) {
        return builder;
    }

    @Override
    public final String complete(LlmRequest request) {
        try {
            JsonNode body = send(request);
            String text = extract(body);
            if (text == null || text.isBlank()) {
                throw new LlmException(name() + " returned an empty completion");
            }
            return text.trim();
        } catch (RestClientException e) {
            throw new LlmException(name() + " request failed: " + e.getMessage());
        }
    }

    protected abstract JsonNode send(LlmRequest request);

    protected abstract String extract(JsonNode body);
}
