package com.finpilot.goal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Pure savings maths: months remaining, required monthly saving and, when the user's average surplus falls
 * short, a set of category cuts (largest discretionary categories first, each capped at 30%) that closes the gap.
 */
public final class SavingsPlanner {
    static final BigDecimal MAX_CUT = new BigDecimal("0.30");

    public record Cut(String category, BigDecimal currentMonthly, BigDecimal suggestedMonthly, BigDecimal saving) {}

    public record Plan(int monthsRemaining, BigDecimal requiredMonthly, BigDecimal averageMonthlySurplus,
                       boolean feasible, List<Cut> cuts) {}

    private SavingsPlanner() {}

    public static int monthsRemaining(LocalDate today, LocalDate target) {
        long months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), target.withDayOfMonth(1));
        return (int) Math.max(1, months);
    }

    public static BigDecimal requiredMonthly(BigDecimal target, BigDecimal saved, int months) {
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO);
        return remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    public static Plan plan(BigDecimal target, BigDecimal saved, LocalDate today, LocalDate targetDate,
                            BigDecimal averageSurplus, Map<String, BigDecimal> discretionaryMonthly) {
        int months = monthsRemaining(today, targetDate);
        BigDecimal required = requiredMonthly(target, saved, months);
        BigDecimal surplus = averageSurplus.setScale(2, RoundingMode.HALF_UP);
        BigDecimal gap = required.subtract(surplus.max(BigDecimal.ZERO));
        List<Cut> cuts = new ArrayList<>();
        if (gap.signum() > 0) {
            List<Map.Entry<String, BigDecimal>> ordered = discretionaryMonthly.entrySet().stream()
                    .filter(e -> e.getValue().signum() > 0)
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                    .toList();
            BigDecimal left = gap;
            for (var e : ordered) {
                if (left.signum() <= 0) {
                    break;
                }
                BigDecimal cap = e.getValue().multiply(MAX_CUT).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saving = cap.min(left);
                cuts.add(new Cut(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP),
                        e.getValue().subtract(saving).setScale(2, RoundingMode.HALF_UP), saving));
                left = left.subtract(saving);
            }
        }
        return new Plan(months, required, surplus, gap.signum() <= 0, cuts);
    }
}
