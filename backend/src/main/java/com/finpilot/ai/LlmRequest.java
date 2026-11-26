package com.finpilot.ai;

import java.util.Map;

/**
 * Provider-neutral completion request. {@code context} carries structured inputs (e.g. the raw question or
 * the computed savings plan) so the offline provider can answer deterministically without parsing prompts.
 */
public record LlmRequest(LlmTask task, String system, String prompt, Map<String, Object> context, int maxTokens) {}
