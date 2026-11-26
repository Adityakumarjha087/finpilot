package com.finpilot.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** Wire contracts of the Python ML service (snake_case where the service uses it). */
public final class MlDtos {
    private MlDtos() {}

    public record CategorizeItem(String id, String description, double amount) {}

    public record CategorizeRequest(List<CategorizeItem> items) {}

    public record Alternative(String category, double probability) {}

    public record Prediction(String id, String category, double confidence, List<Alternative> alternatives) {}

    public record CategorizeResponse(@JsonProperty("model_version") int modelVersion, List<Prediction> predictions) {}

    public record Correction(String description, double amount, String category) {}

    public record TrainRequest(List<Correction> corrections) {}

    public record ModelInfo(int version, @JsonProperty("trained_at") String trainedAt,
                            @JsonProperty("n_samples") int nSamples,
                            @JsonProperty("n_corrections") int nCorrections,
                            @JsonProperty("holdout_accuracy") double holdoutAccuracy,
                            @JsonProperty("holdout_macro_f1") double holdoutMacroF1, List<String> classes) {}

    public record SeriesPoint(String period, double amount) {}

    public record Series(String key, List<SeriesPoint> history) {}

    public record ForecastRequest(List<Series> series, int horizon, double confidence) {}

    public record ForecastPoint(String period, double mean, double lower, double upper) {}

    public record SeriesForecast(String key, String method, List<ForecastPoint> points) {}

    public record ForecastResponse(int horizon, double confidence, List<SeriesForecast> forecasts) {}

    public record AnomalyTxn(String id, @JsonProperty("posted_at") Instant postedAt, double amount, String category,
                             String merchant) {}

    public record AnomalyRequest(List<AnomalyTxn> transactions) {}

    public record AnomalyOut(String id, double score, List<String> reasons) {}

    public record AnomalyResponse(int scanned, List<AnomalyOut> anomalies) {}
}
