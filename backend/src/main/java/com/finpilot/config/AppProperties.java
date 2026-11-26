package com.finpilot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Strongly typed application configuration, bound from {@code finpilot.*}. */
@Validated
@ConfigurationProperties(prefix = "finpilot")
public record AppProperties(
        @NotBlank String timezone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Encryption encryption,
        @Valid @NotNull Ml ml,
        @Valid @NotNull Ai ai,
        @Valid @NotNull ReadonlyDb readonlyDb,
        @Valid @NotNull RateLimit rateLimit,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Retrain retrain,
        @Valid @NotNull Seed seed) {

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTtl,
                      @NotNull Duration refreshTtl) {}

    public record Encryption(@NotBlank String key) {}

    public record Ml(@NotBlank String baseUrl, @NotBlank String token, @NotNull Duration timeout) {}

    public record Ai(@NotBlank String provider, String model, String apiKey, String baseUrl, @NotNull Duration timeout) {}

    public record ReadonlyDb(@NotBlank String url, @NotBlank String username, @NotBlank String password) {}

    public record RateLimit(@NotBlank String backend, @Min(1) int loginPerMinute, @Min(1) int apiPerMinute) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Retrain(@Min(1) int minCorrections, @NotBlank String cron) {}

    public record Seed(boolean enabled) {}
}
