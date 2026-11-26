package com.finpilot.forecast;

import com.finpilot.dashboard.SpendQueries;
import com.finpilot.dashboard.SpendQueries.CategoryMonth;
import com.finpilot.ml.MlClient;
import com.finpilot.ml.MlDtos.ForecastPoint;
import com.finpilot.ml.MlDtos.ForecastRequest;
import com.finpilot.ml.MlDtos.ForecastResponse;
import com.finpilot.ml.MlDtos.Series;
import com.finpilot.ml.MlDtos.SeriesForecast;
import com.finpilot.ml.MlDtos.SeriesPoint;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Builds monthly spend series per category from the last 12 complete months and asks the ML service for a
 * forecast with 80% prediction intervals. The current (partial) month is excluded from training so it does not
 * drag the trend down.
 */
@Service
public class ForecastService {
    private static final String TOTAL = "__total__";
    private static final int HISTORY_MONTHS = 12;
    private final SpendQueries spend;
    private final MlClient ml;
    private final Clock clock;
    private final ZoneId zone;

    public ForecastService(SpendQueries spend, MlClient ml, Clock clock, ZoneId appZone) {
        this.spend = spend;
        this.ml = ml;
        this.clock = clock;
        this.zone = appZone;
    }

    public record MonthAmount(String month, double amount) {}

    public record MonthForecast(String month, double mean, double lower, double upper) {}

    public record CategoryForecast(String category, String method, List<MonthAmount> history,
                                   List<MonthForecast> forecast) {}

    public record TotalForecast(List<MonthAmount> history, List<MonthForecast> forecast) {}

    public record ForecastResult(int horizon, double confidence, Instant generatedAt, List<CategoryForecast> categories,
                                 TotalForecast total) {}

    public ForecastResult forecast(long userId, int horizon) {
        YearMonth lastComplete = YearMonth.now(clock.withZone(zone)).minusMonths(1);
        YearMonth from = lastComplete.minusMonths(HISTORY_MONTHS - 1L);
        Map<String, TreeMap<YearMonth, BigDecimal>> byCategory = new LinkedHashMap<>();
        TreeMap<YearMonth, BigDecimal> total = new TreeMap<>();
        for (CategoryMonth cm : spend.spendByCategoryMonth(userId, from, lastComplete)) {
            byCategory.computeIfAbsent(cm.category(), k -> new TreeMap<>()).put(cm.month(), cm.spent());
            total.merge(cm.month(), cm.spent(), BigDecimal::add);
        }
        List<Series> series = new ArrayList<>();
        Map<String, List<MonthAmount>> histories = new LinkedHashMap<>();
        byCategory.forEach((cat, months) -> {
            if (months.size() >= 2) {
                List<MonthAmount> h = densify(months, from, lastComplete);
                histories.put(cat, h);
                series.add(toSeries(cat, h));
            }
        });
        List<MonthAmount> totalHistory = densify(total, from, lastComplete);
        if (total.isEmpty()) {
            return new ForecastResult(horizon, 0.8, clock.instant(), List.of(), new TotalForecast(List.of(), List.of()));
        }
        series.add(toSeries(TOTAL, totalHistory));
        ForecastResponse resp = ml.forecast(new ForecastRequest(series, horizon, 0.8));
        List<CategoryForecast> cats = new ArrayList<>();
        List<MonthForecast> totalFc = List.of();
        for (SeriesForecast sf : resp.forecasts()) {
            List<MonthForecast> points = sf.points().stream().map(ForecastService::point).toList();
            if (TOTAL.equals(sf.key())) {
                totalFc = points;
            } else {
                cats.add(new CategoryForecast(sf.key(), sf.method(), histories.get(sf.key()), points));
            }
        }
        cats.sort((a, b) -> Double.compare(b.forecast().getFirst().mean(), a.forecast().getFirst().mean()));
        return new ForecastResult(horizon, resp.confidence(), clock.instant(), cats,
                new TotalForecast(totalHistory, totalFc));
    }

    private static MonthForecast point(ForecastPoint p) {
        return new MonthForecast(p.period(), p.mean(), p.lower(), p.upper());
    }

    /** Fills missing months with zero starting from the first month with data. */
    private static List<MonthAmount> densify(TreeMap<YearMonth, BigDecimal> months, YearMonth from, YearMonth to) {
        List<MonthAmount> out = new ArrayList<>();
        if (months.isEmpty()) {
            return out;
        }
        YearMonth start = months.firstKey().isAfter(from) ? months.firstKey() : from;
        for (YearMonth m = start; !m.isAfter(to); m = m.plusMonths(1)) {
            out.add(new MonthAmount(m.toString(), months.getOrDefault(m, BigDecimal.ZERO).doubleValue()));
        }
        return out;
    }

    private static Series toSeries(String key, List<MonthAmount> history) {
        return new Series(key, history.stream().map(h -> new SeriesPoint(h.month(), h.amount())).toList());
    }
}
