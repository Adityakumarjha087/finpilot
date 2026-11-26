package com.finpilot.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionDto(long id, Instant postedAt, String description, String merchant, BigDecimal amount,
                             String category, CategorySource categorySource, Double categoryConfidence, String notes,
                             Anomaly anomaly) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Anomaly(AnomalyStatus status, Double score, List<String> reasons) {}

    public static TransactionDto of(BankTransaction t) {
        return new TransactionDto(t.getId(), t.getPostedAt(), t.getDescription(), t.getMerchant(), t.getAmount(),
                t.getCategory(), t.getCategorySource(), t.getCategoryConfidence(), t.getNotes(),
                new Anomaly(t.getAnomalyStatus(), t.getAnomalyScore(), reasons(t.getAnomalyReasons())));
    }

    static List<String> reasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
