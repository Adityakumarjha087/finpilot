package com.finpilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import java.time.Duration;
import java.util.stream.Collectors;

/** Claude via the official Anthropic Java SDK. */
public class AnthropicLlmProvider implements LlmProvider {
    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    private final AnthropicClient client;
    private final String model;

    public AnthropicLlmProvider(String apiKey, String model, String baseUrl, Duration timeout) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder().apiKey(apiKey).timeout(timeout);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    @Override
    public String name() {
        return "anthropic:" + model;
    }

    @Override
    public String complete(LlmRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxTokens())
                .system(request.system())
                .addUserMessage(request.prompt())
                .build();
        try {
            Message message = client.messages().create(params);
            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (text.isEmpty()) {
                throw new LlmException("Claude returned no text (stop reason: "
                        + message.stopReason().map(Object::toString).orElse("unknown") + ")");
            }
            return text;
        } catch (AnthropicException e) {
            throw new LlmException("Claude request failed: " + e.getMessage());
        }
    }
}
