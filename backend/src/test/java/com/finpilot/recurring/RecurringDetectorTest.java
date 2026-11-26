package com.finpilot.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpilot.recurring.RecurringDetector.Cadence;
import com.finpilot.recurring.RecurringDetector.Charge;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private static List<Charge> monthly(String merchant, String amount, int months, LocalDate last) {
        List<Charge> out = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            out.add(new Charge(merchant, "Subscriptions", last.minusMonths(i), new BigDecimal(amount)));
        }
        return out;
    }

    @Test
    void detectsMonthlySubscriptionAndWeeklyBill() {
        List<Charge> charges = new ArrayList<>(monthly("NETFLIX", "-649", 6, LocalDate.of(2026, 11, 5)));
        for (int i = 0; i < 8; i++) {
            charges.add(new Charge("GYM", "Health", TODAY.minusWeeks(i), new BigDecimal(i % 2 == 0 ? "-500" : "-560")));
        }
        var found = RecurringDetector.detect(charges, TODAY);
        assertThat(found).hasSize(2);
        var netflix = found.stream().filter(r -> r.merchant().equals("NETFLIX")).findFirst().orElseThrow();
        assertThat(netflix.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(netflix.subscription()).isTrue();
        assertThat(netflix.averageAmount()).isEqualByComparingTo("649");
        assertThat(netflix.nextExpected()).isAfter(LocalDate.of(2026, 12, 1));
        assertThat(netflix.monthlyCost().doubleValue()).isBetween(600.0, 700.0);
        var gym = found.stream().filter(r -> r.merchant().equals("GYM")).findFirst().orElseThrow();
        assertThat(gym.cadence()).isEqualTo(Cadence.WEEKLY);
        assertThat(gym.subscription()).isFalse();
        assertThat(found.getFirst().monthlyCost()).isGreaterThanOrEqualTo(found.getLast().monthlyCost());
    }

    @Test
    void ignoresIrregularCancelledVariableAndSparse() {
        List<Charge> charges = new ArrayList<>();
        charges.addAll(monthly("OLDSUB", "-199", 5, LocalDate.of(2026, 5, 1))); // cancelled
        charges.addAll(monthly("TWICE", "-99", 2, TODAY)); // too few
        int[] gaps = {1, 9, 3, 20, 2};
        LocalDate d = TODAY;
        for (int g : gaps) {
            d = d.minusDays(g);
            charges.add(new Charge("SWIGGY", "Food", d, new BigDecimal("-300")));
        }
        charges.addAll(List.of(new Charge("SHOP", "Shopping", TODAY, new BigDecimal("-100")),
                new Charge("SHOP", "Shopping", TODAY.minusMonths(1), new BigDecimal("-5000")),
                new Charge("SHOP", "Shopping", TODAY.minusMonths(2), new BigDecimal("-40"))));
        assertThat(RecurringDetector.detect(charges, TODAY)).isEmpty();
    }
}
