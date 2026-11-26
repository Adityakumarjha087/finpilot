package com.finpilot.goal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SavingsPlannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void feasiblePlanHasNoCuts() {
        var plan = SavingsPlanner.plan(new BigDecimal("60000"), new BigDecimal("12000"), TODAY,
                LocalDate.of(2027, 3, 1), new BigDecimal("20000"), Map.of("Food", new BigDecimal("8000")));
        assertThat(plan.monthsRemaining()).isEqualTo(4);
        assertThat(plan.requiredMonthly()).isEqualByComparingTo("12000");
        assertThat(plan.feasible()).isTrue();
        assertThat(plan.cuts()).isEmpty();
    }

    @Test
    void shortfallProducesCappedCutsLargestFirst() {
        var plan = SavingsPlanner.plan(new BigDecimal("100000"), BigDecimal.ZERO, TODAY, LocalDate.of(2027, 1, 1),
                new BigDecimal("40000"), Map.of("Food", new BigDecimal("10000"), "Shopping", new BigDecimal("6000"),
                        "Travel", BigDecimal.ZERO));
        assertThat(plan.monthsRemaining()).isEqualTo(2);
        assertThat(plan.requiredMonthly()).isEqualByComparingTo("50000");
        assertThat(plan.feasible()).isFalse();
        assertThat(plan.cuts()).hasSize(2);
        assertThat(plan.cuts().get(0).category()).isEqualTo("Food");
        assertThat(plan.cuts().get(0).saving()).isEqualByComparingTo("3000");
        assertThat(plan.cuts().get(1).saving()).isEqualByComparingTo("1800");
        assertThat(plan.cuts().get(0).suggestedMonthly()).isEqualByComparingTo("7000");
    }

    @Test
    void edgeCases() {
        assertThat(SavingsPlanner.monthsRemaining(TODAY, TODAY)).isEqualTo(1);
        assertThat(SavingsPlanner.requiredMonthly(new BigDecimal("10"), new BigDecimal("20"), 3)).isEqualByComparingTo("0");
        assertThat(SavingsPlanner.requiredMonthly(new BigDecimal("100"), BigDecimal.ZERO, 3)).isEqualByComparingTo("33.34");
    }
}
