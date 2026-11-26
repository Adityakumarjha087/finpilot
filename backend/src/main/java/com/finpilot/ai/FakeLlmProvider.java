package com.finpilot.ai;

import com.finpilot.assistant.TemplateSqlGenerator;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic offline provider (the default). Text-to-SQL is answered by an intent/template engine and
 * savings advice by a templated summary of the computed plan, so the app, tests and CI need no API key.
 * Its SQL still goes through the same validator and read-only role as any real model's output.
 */
public class FakeLlmProvider implements LlmProvider {
    private final TemplateSqlGenerator sql;

    public FakeLlmProvider(TemplateSqlGenerator sql) {
        this.sql = sql;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public String complete(LlmRequest request) {
        return switch (request.task()) {
            case TEXT_TO_SQL -> sql.generate(String.valueOf(request.context().get("question")));
            case SAVINGS_ADVICE -> advice(request.context());
        };
    }

    private static String advice(Map<String, Object> c) {
        String goal = String.valueOf(c.get("goalName"));
        double required = ((Number) c.get("requiredMonthly")).doubleValue();
        double surplus = ((Number) c.get("averageMonthlySurplus")).doubleValue();
        int months = ((Number) c.get("monthsRemaining")).intValue();
        String topCut = String.valueOf(c.getOrDefault("topCutCategory", ""));
        double topSaving = ((Number) c.getOrDefault("topCutSaving", 0)).doubleValue();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "To reach \"%s\" in %d month%s, set aside %s every month. ", goal, months,
                months == 1 ? "" : "s", inr(required)));
        if (surplus >= required) {
            sb.append(String.format(Locale.ROOT,
                    "Your recent average surplus of %s covers this, so automate a transfer of %s on payday to keep "
                            + "it on track.", inr(surplus), inr(required)));
        } else {
            sb.append(String.format(Locale.ROOT, "Your recent average surplus is %s, which leaves a gap of %s. ",
                    inr(Math.max(surplus, 0)), inr(required - Math.max(surplus, 0))));
            if (!topCut.isBlank() && topSaving > 0) {
                sb.append(String.format(Locale.ROOT,
                        "The biggest lever is %s: trimming it by %s a month closes most of the gap. ", topCut,
                        inr(topSaving)));
            }
            sb.append("Alternatively, extend the target date or lower the target amount.");
        }
        return sb.toString();
    }

    static String inr(double v) {
        return "Rs " + String.format(Locale.ROOT, "%,.0f", v);
    }
}
