package com.finpilot.recurring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure recurring-payment detector. Groups expenses by merchant, then requires (a) at least three charges,
 * (b) a median gap matching a known cadence, (c) >= 70% of gaps within tolerance of that median and
 * (d) the series still being active. A series with near-constant amounts on a monthly/yearly cadence is
 * reported as a subscription.
 */
public final class RecurringDetector {
    public enum Cadence {
        WEEKLY(7, 2), MONTHLY(30, 5), QUARTERLY(91, 10), YEARLY(365, 20);

        final int days;
        final int tolerance;

        Cadence(int days, int tolerance) {
            this.days = days;
            this.tolerance = tolerance;
        }

        static Cadence match(double medianGap) {
            for (Cadence c : values()) {
                if (Math.abs(medianGap - c.days) <= c.tolerance) {
                    return c;
                }
            }
            return null;
        }
    }

    public record Charge(String merchant, String category, LocalDate date, BigDecimal amount) {}

    public record Recurring(String merchant, String category, Cadence cadence, BigDecimal averageAmount,
                            LocalDate lastCharged, LocalDate nextExpected, int occurrences, BigDecimal monthlyCost,
                            boolean subscription) {}

    private RecurringDetector() {}

    public static List<Recurring> detect(List<Charge> charges, LocalDate today) {
        Map<String, List<Charge>> byMerchant = new LinkedHashMap<>();
        for (Charge c : charges) {
            byMerchant.computeIfAbsent(c.merchant(), k -> new ArrayList<>()).add(c);
        }
        List<Recurring> out = new ArrayList<>();
        for (var entry : byMerchant.entrySet()) {
            List<Charge> list = new ArrayList<>(entry.getValue());
            if (list.size() < 3) {
                continue;
            }
            list.sort(Comparator.comparing(Charge::date));
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < list.size(); i++) {
                long gap = ChronoUnit.DAYS.between(list.get(i - 1).date(), list.get(i).date());
                if (gap > 0) {
                    gaps.add(gap);
                }
            }
            if (gaps.size() < 2) {
                continue;
            }
            double median = median(gaps);
            Cadence cadence = Cadence.match(median);
            if (cadence == null) {
                continue;
            }
            long regular = gaps.stream().filter(g -> Math.abs(g - median) <= cadence.tolerance).count();
            if (regular < Math.ceil(gaps.size() * 0.7)) {
                continue;
            }
            LocalDate last = list.getLast().date();
            if (ChronoUnit.DAYS.between(last, today) > median * 1.5 + cadence.tolerance) {
                continue; // stopped / cancelled
            }
            double[] amounts = list.stream().mapToDouble(c -> c.amount().abs().doubleValue()).toArray();
            double mean = java.util.Arrays.stream(amounts).average().orElse(0);
            double sd = Math.sqrt(java.util.Arrays.stream(amounts).map(a -> (a - mean) * (a - mean)).sum()
                    / amounts.length);
            double cv = mean > 0 ? sd / mean : 1;
            if (cv > 0.35) {
                continue; // too variable to be a recurring bill
            }
            BigDecimal avg = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
            BigDecimal monthly = BigDecimal.valueOf(mean * 30.44 / median).setScale(2, RoundingMode.HALF_UP);
            boolean subscription = cv <= 0.05 && (cadence == Cadence.MONTHLY || cadence == Cadence.YEARLY
                    || cadence == Cadence.QUARTERLY);
            out.add(new Recurring(entry.getKey(), list.getLast().category(), cadence, avg, last,
                    last.plusDays(Math.round(median)), list.size(), monthly, subscription));
        }
        out.sort(Comparator.comparing(Recurring::monthlyCost).reversed());
        return out;
    }

    private static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
