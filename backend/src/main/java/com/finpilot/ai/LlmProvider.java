package com.finpilot.ai;

/** One interface for every LLM backend (fake, Anthropic, OpenAI, Gemini, Ollama), selected by LLM_PROVIDER. */
public interface LlmProvider {
    String name();

    String complete(LlmRequest request);
}
