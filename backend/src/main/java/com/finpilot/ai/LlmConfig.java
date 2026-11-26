package com.finpilot.ai;

import com.finpilot.assistant.TemplateSqlGenerator;
import com.finpilot.config.AppProperties;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    LlmProvider llmProvider(AppProperties props, RestClient.Builder builder, TemplateSqlGenerator templates) {
        AppProperties.Ai ai = props.ai();
        String provider = ai.provider().toLowerCase(Locale.ROOT).trim();
        LlmProvider p = switch (provider) {
            case "fake" -> new FakeLlmProvider(templates);
            case "anthropic" -> new AnthropicLlmProvider(requireKey(ai), ai.model(), ai.baseUrl(), ai.timeout());
            case "openai" -> new OpenAiLlmProvider(builder, requireKey(ai), ai.model(), ai.baseUrl(), ai.timeout());
            case "gemini" -> new GeminiLlmProvider(builder, requireKey(ai), ai.model(), ai.baseUrl(), ai.timeout());
            case "ollama" -> new OllamaLlmProvider(builder, ai.model(), ai.baseUrl(), ai.timeout());
            default -> throw new IllegalStateException("Unknown LLM_PROVIDER '" + ai.provider()
                    + "' (use fake, anthropic, openai, gemini or ollama)");
        };
        log.info("LLM provider: {}", p.name());
        return p;
    }

    private static String requireKey(AppProperties.Ai ai) {
        if (ai.apiKey() == null || ai.apiKey().isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is required for provider " + ai.provider());
        }
        return ai.apiKey();
    }
}
