package com.finpilot.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AlertLevelTest {
    @Test
    void thresholds() {
        assertThat(AlertLevel.of(new BigDecimal("0.79"))).isEqualTo(AlertLevel.OK);
        assertThat(AlertLevel.of(new BigDecimal("0.80"))).isEqualTo(AlertLevel.WARNING);
        assertThat(AlertLevel.of(new BigDecimal("0.99"))).isEqualTo(AlertLevel.WARNING);
        assertThat(AlertLevel.of(BigDecimal.ONE)).isEqualTo(AlertLevel.EXCEEDED);
        assertThat(AlertLevel.of(new BigDecimal("1.7"))).isEqualTo(AlertLevel.EXCEEDED);
    }

    @Test
    void statusComputesPercent() {
        var s = BudgetService.status(new Budget(1L, "Food", new BigDecimal("5000")), new BigDecimal("4500"),
                YearMonth.of(2026, 11));
        assertThat(s.percent()).isEqualTo(0.9);
        assertThat(s.alertLevel()).isEqualTo(AlertLevel.WARNING);
        assertThat(s.month()).isEqualTo("2026-11");
    }
}
