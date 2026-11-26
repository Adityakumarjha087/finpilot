package com.finpilot.dashboard;

import com.finpilot.budget.BudgetService;
import com.finpilot.transaction.AnomalyStatus;
import com.finpilot.transaction.TransactionRepository;
import com.finpilot.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final SpendQueries spend;
    private final TransactionRepository transactions;
    private final BudgetService budgets;
    private final UserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    public DashboardService(SpendQueries spend, TransactionRepository transactions, BudgetService budgets,
                            UserRepository users, Clock clock, ZoneId appZone) {
        this.spend = spend;
        this.transactions = transactions;
        this.budgets = budgets;
        this.users = users;
        this.clock = clock;
        this.zone = appZone;
    }

    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal net, double savingsRate) {}

    public record Monthly(String month, BigDecimal income, BigDecimal expense, BigDecimal net) {}

    public record CategoryAmount(String category, BigDecimal amount) {}

    public record NetWorthPoint(String month, BigDecimal value) {}

    public record Dashboard(String currency, Totals totals, List<Monthly> monthly,
                            List<CategoryAmount> categoryBreakdown, List<NetWorthPoint> netWorth,
                            long flaggedAnomalies, long budgetAlerts, long transactionCount) {}

    @Transactional(readOnly = true)
    public Dashboard build(long userId, int months) {
        YearMonth current = YearMonth.now(clock.withZone(zone));
        YearMonth from = current.minusMonths(months - 1L);
        List<Monthly> monthly = spend.monthlyFlows(userId, from, current).stream()
                .map(f -> new Monthly(f.month().toString(), f.income(), f.expense(), f.income().subtract(f.expense())))
                .toList();
        Monthly now = monthly.getLast();
        double savingsRate = now.income().signum() > 0
                ? now.net().divide(now.income(), 4, RoundingMode.HALF_UP).doubleValue() : 0.0;
        Totals totals = new Totals(now.income(), now.expense(), now.net(), savingsRate);

        List<CategoryAmount> breakdown = spend.spendByCategory(userId, current).entrySet().stream()
                .map(e -> new CategoryAmount(e.getKey(), e.getValue())).toList();

        BigDecimal opening = users.findById(userId).map(u -> u.getOpeningBalance()).orElse(BigDecimal.ZERO);
        BigDecimal running = opening.add(spend.netBefore(userId, from));
        List<NetWorthPoint> netWorth = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> e : spend.monthlyNet(userId, from, current).entrySet()) {
            running = running.add(e.getValue());
            netWorth.add(new NetWorthPoint(e.getKey().toString(), running));
        }
        long flagged = transactions.countByUserIdAndAnomalyStatus(userId, AnomalyStatus.FLAGGED);
        long alerts = budgets.alerts(userId, current).size();
        return new Dashboard("INR", totals, monthly, breakdown, netWorth, flagged, alerts,
                transactions.countByUserId(userId));
    }
}
