package com.finpilot.ml;

import com.finpilot.config.AppProperties;
import com.finpilot.ml.MlDtos.AnomalyRequest;
import com.finpilot.ml.MlDtos.AnomalyResponse;
import com.finpilot.ml.MlDtos.CategorizeRequest;
import com.finpilot.ml.MlDtos.CategorizeResponse;
import com.finpilot.ml.MlDtos.ForecastRequest;
import com.finpilot.ml.MlDtos.ForecastResponse;
import com.finpilot.ml.MlDtos.ModelInfo;
import com.finpilot.ml.MlDtos.TrainRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP client for the Python ML service, authenticated with a shared internal token. */
@Component
public class MlClient {
    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private final RestClient http;

    public MlClient(RestClient.Builder builder, AppProperties props) {
        Duration timeout = props.ml().timeout();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(timeout);
        this.http = builder.clone()
                .baseUrl(props.ml().baseUrl())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", props.ml().token())
                .build();
    }

    public CategorizeResponse categorize(CategorizeRequest request) {
        return call("categorize", () -> http.post().uri("/v1/categorize").body(request).retrieve()
                .body(CategorizeResponse.class));
    }

    public ModelInfo train(TrainRequest request) {
        return call("train", () -> http.post().uri("/v1/train").body(request).retrieve().body(ModelInfo.class));
    }

    public ModelInfo modelInfo() {
        return call("model", () -> http.get().uri("/v1/model").retrieve().body(ModelInfo.class));
    }

    public ForecastResponse forecast(ForecastRequest request) {
        return call("forecast", () -> http.post().uri("/v1/forecast").body(request).retrieve()
                .body(ForecastResponse.class));
    }

    public AnomalyResponse anomalies(AnomalyRequest request) {
        return call("anomalies", () -> http.post().uri("/v1/anomalies").body(request).retrieve()
                .body(AnomalyResponse.class));
    }

    public List<Map<String, Object>> syntheticTransactions(int months, int seed) {
        return call("synthetic", () -> http.get().uri("/v1/synthetic/transactions?months={m}&seed={s}", months, seed)
                .retrieve().body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));
    }

    private <T> T call(String op, java.util.function.Supplier<T> fn) {
        try {
            T result = fn.get();
            if (result == null) {
                throw new MlUnavailableException("ML service returned an empty response");
            }
            return result;
        } catch (RestClientException e) {
            log.warn("ML service call '{}' failed: {}", op, e.getMessage());
            throw new MlUnavailableException("ML service is unavailable, try again shortly");
        }
    }
}
