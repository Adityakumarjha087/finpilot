package com.finpilot.goal;

import com.finpilot.ai.LlmException;
import com.finpilot.ai.LlmProvider;
import com.finpilot.ai.LlmRequest;
import com.finpilot.ai.LlmTask;
import com.finpilot.audit.AuditService;
import com.finpilot.common.ApiException;
import com.finpilot.dashboard.SpendQueries;
import com.finpilot.dashboard.SpendQueries.MonthlyFlow;
import com.finpilot.goal.SavingsPlanner.Cut;
import com.finpilot.goal.SavingsPlanner.Plan;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.transaction.Categories;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {
    private static final Logger log = LoggerFactory.getLogger(GoalService.class);
    private static final int LOOKBACK_MONTHS = 3;
    private final GoalRepository repo;
    private final SpendQueries spend;
    private final LlmProvider llm;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    public GoalService(GoalRepository repo, SpendQueries spend, LlmProvider llm, AuditService audit, Clock clock,
                       ZoneId appZone) {
        this.repo = repo;
        this.spend = spend;
        this.llm = llm;
        this.audit = audit;
        this.clock = clock;
        this.zone = appZone;
    }

    public record GoalDto(long id, String name, BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate,
                          double progress, int monthsRemaining, BigDecimal requiredMonthly) {}

    public record Suggestion(long goalId, BigDecimal requiredMonthly, BigDecimal averageMonthlySurplus,
                             boolean feasible, List<Cut> cuts, String advice, String provider) {}

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    GoalDto toDto(Goal g) {
        int months = SavingsPlanner.monthsRemaining(today(), g.getTargetDate());
        double progress = g.getSavedAmount().divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE).doubleValue();
        return new GoalDto(g.getId(), g.getName(), g.getTargetAmount(), g.getSavedAmount(), g.getTargetDate(), progress,
                months, SavingsPlanner.requiredMonthly(g.getTargetAmount(), g.getSavedAmount(), months));
    }

    @Transactional(readOnly = true)
    public List<GoalDto> list(long userId) {
        return repo.findByUserIdOrderByTargetDateAsc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public GoalDto create(AuthenticatedUser user, String name, BigDecimal target, BigDecimal saved, LocalDate date) {
        validate(target, saved, date);
        Goal g = repo.save(new Goal(user.id(), name.trim(), target, saved, date));
        audit.record(user.id(), user.email(), "GOAL_CREATED", "GOAL", g.getId(), name);
        return toDto(g);
    }

    @Transactional
    public GoalDto update(AuthenticatedUser user, long id, String name, BigDecimal target, BigDecimal saved,
                          LocalDate date) {
        validate(target, saved, date);
        Goal g = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Goal"));
        g.update(name.trim(), target, saved, date);
        audit.record(user.id(), user.email(), "GOAL_UPDATED", "GOAL", id, name);
        return toDto(repo.save(g));
    }

    @Transactional
    public void delete(AuthenticatedUser user, long id) {
        Goal g = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Goal"));
        repo.delete(g);
        audit.record(user.id(), user.email(), "GOAL_DELETED", "GOAL", id, g.getName());
    }

    @Transactional(readOnly = true)
    public Suggestion suggest(AuthenticatedUser user, long id) {
        Goal g = repo.findByIdAndUserId(id, user.id()).orElseThrow(() -> ApiException.notFound("Goal"));
        YearMonth last = YearMonth.now(clock.withZone(zone)).minusMonths(1);
        YearMonth first = last.minusMonths(LOOKBACK_MONTHS - 1L);
        List<MonthlyFlow> flows = spend.monthlyFlows(user.id(), first, last);
        BigDecimal surplus = flows.stream().map(f -> f.income().subtract(f.expense()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> discretionary = new HashMap<>();
        spend.spendByCategoryMonth(user.id(), first, last).stream()
                .filter(cm -> Categories.DISCRETIONARY.contains(cm.category()))
                .forEach(cm -> discretionary.merge(cm.category(), cm.spent(), BigDecimal::add));
        discretionary.replaceAll((k, v) -> v.divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP));

        Plan plan = SavingsPlanner.plan(g.getTargetAmount(), g.getSavedAmount(), today(), g.getTargetDate(), surplus,
                discretionary);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("goalName", g.getName());
        ctx.put("requiredMonthly", plan.requiredMonthly());
        ctx.put("averageMonthlySurplus", plan.averageMonthlySurplus());
        ctx.put("monthsRemaining", plan.monthsRemaining());
        ctx.put("feasible", plan.feasible());
        if (!plan.cuts().isEmpty()) {
            ctx.put("topCutCategory", plan.cuts().getFirst().category());
            ctx.put("topCutSaving", plan.cuts().getFirst().saving());
        }
        String prompt = "Goal: " + g.getName() + "\nTarget: " + g.getTargetAmount() + " INR by " + g.getTargetDate()
                + "\nAlready saved: " + g.getSavedAmount() + "\nMonths remaining: " + plan.monthsRemaining()
                + "\nRequired monthly saving: " + plan.requiredMonthly() + "\nAverage monthly surplus (last 3 months): "
                + plan.averageMonthlySurplus() + "\nSuggested cuts: " + plan.cuts().stream()
                .map(c -> c.category() + " -" + c.saving()).collect(Collectors.joining(", "));
        String advice;
        String provider = llm.name();
        try {
            advice = llm.complete(new LlmRequest(LlmTask.SAVINGS_ADVICE,
                    "You are a concise, practical personal-finance coach. Using ONLY the numbers given, write 2-4 "
                            + "sentences of actionable monthly saving advice in plain English. Amounts are INR.",
                    prompt, ctx, 400));
        } catch (LlmException e) {
            log.warn("LLM advice failed, falling back to template: {}", e.getMessage());
            advice = new com.finpilot.ai.FakeLlmProvider(null).complete(new LlmRequest(LlmTask.SAVINGS_ADVICE, "",
                    prompt, ctx, 400));
            provider = "fake";
        }
        return new Suggestion(g.getId(), plan.requiredMonthly(), plan.averageMonthlySurplus(), plan.feasible(),
                plan.cuts(), advice, provider);
    }

    private static void validate(BigDecimal target, BigDecimal saved, LocalDate date) {
        if (saved.compareTo(target) > 0) {
            throw ApiException.badRequest("savedAmount cannot exceed targetAmount");
        }
        if (date == null) {
            throw ApiException.badRequest("targetDate is required");
        }
    }
}
