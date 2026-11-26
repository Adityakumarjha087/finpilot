package com.finpilot.budget;

import com.finpilot.audit.AuditService;
import com.finpilot.common.ApiException;
import com.finpilot.dashboard.SpendQueries;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.transaction.Categories;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    private final BudgetRepository repo;
    private final SpendQueries spend;
    private final AuditService audit;

    public BudgetService(BudgetRepository repo, SpendQueries spend, AuditService audit) {
        this.repo = repo;
        this.spend = spend;
        this.audit = audit;
    }

    public record BudgetStatus(Long id, String category, BigDecimal monthlyLimit, BigDecimal spent, double percent,
                               AlertLevel alertLevel, String month) {}

    public record BudgetAlert(Long budgetId, String category, AlertLevel alertLevel, double percent, String message) {}

    @Transactional(readOnly = true)
    public List<BudgetStatus> statuses(long userId, YearMonth month) {
        Map<String, BigDecimal> spent = spend.spendByCategory(userId, month);
        return repo.findByUserIdOrderByCategoryAsc(userId).stream()
                .map(b -> status(b, spent.getOrDefault(b.getCategory(), BigDecimal.ZERO), month))
                .toList();
    }

    static BudgetStatus status(Budget b, BigDecimal spent, YearMonth month) {
        BigDecimal ratio = spent.divide(b.getMonthlyLimit(), 4, RoundingMode.HALF_UP);
        return new BudgetStatus(b.getId(), b.getCategory(), b.getMonthlyLimit(), spent.setScale(2, RoundingMode.HALF_UP),
                ratio.doubleValue(), AlertLevel.of(ratio), month.toString());
    }

    @Transactional(readOnly = true)
    public List<BudgetAlert> alerts(long userId, YearMonth month) {
        return statuses(userId, month).stream()
                .filter(s -> s.alertLevel() != AlertLevel.OK)
                .map(s -> new BudgetAlert(s.id(), s.category(), s.alertLevel(), s.percent(),
                        s.alertLevel() == AlertLevel.EXCEEDED
                                ? String.format("%s budget exceeded: %.0f%% used", s.category(), s.percent() * 100)
                                : String.format("%s budget at %.0f%% - nearing the limit", s.category(),
                                s.percent() * 100)))
                .toList();
    }

    @Transactional
    public BudgetStatus create(AuthenticatedUser user, String category, BigDecimal limit, YearMonth month) {
        validate(category, limit);
        if (repo.existsByUserIdAndCategory(user.id(), category)) {
            throw ApiException.conflict("A budget for " + category + " already exists");
        }
        Budget b = repo.save(new Budget(user.id(), category, limit));
        audit.record(user.id(), user.email(), "BUDGET_CREATED", "BUDGET", b.getId(), category + "=" + limit);
        return status(b, spend.spendByCategory(user.id(), month).getOrDefault(category, BigDecimal.ZERO), month);
    }

    @Transactional
    public BudgetStatus update(AuthenticatedUser user, long id, String category, BigDecimal limit, YearMonth month) {
        validate(category, limit);
        Budget b = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Budget"));
        if (!b.getCategory().equals(category) && repo.existsByUserIdAndCategory(user.id(), category)) {
            throw ApiException.conflict("A budget for " + category + " already exists");
        }
        b.setCategory(category);
        b.setMonthlyLimit(limit);
        repo.save(b);
        audit.record(user.id(), user.email(), "BUDGET_UPDATED", "BUDGET", id, category + "=" + limit);
        return status(b, spend.spendByCategory(user.id(), month).getOrDefault(category, BigDecimal.ZERO), month);
    }

    @Transactional
    public void delete(AuthenticatedUser user, long id) {
        Budget b = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Budget"));
        repo.delete(b);
        audit.record(user.id(), user.email(), "BUDGET_DELETED", "BUDGET", id, b.getCategory());
    }

    private static void validate(String category, BigDecimal limit) {
        if (!Categories.isValid(category) || Categories.NON_SPENDING.contains(category)
                || Categories.UNCATEGORIZED.equals(category)) {
            throw ApiException.badRequest("Budgets can only be set on spending categories");
        }
        if (limit == null || limit.signum() <= 0) {
            throw ApiException.badRequest("monthlyLimit must be positive");
        }
    }
}
