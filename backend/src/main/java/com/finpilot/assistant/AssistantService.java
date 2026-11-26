package com.finpilot.assistant;

import com.finpilot.ai.LlmProvider;
import com.finpilot.ai.LlmRequest;
import com.finpilot.ai.LlmTask;
import com.finpilot.assistant.ChartSuggester.Chart;
import com.finpilot.assistant.ReadOnlyQueryExecutor.QueryResult;
import com.finpilot.audit.AuditService;
import com.finpilot.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** NL question -> provider SQL -> validator -> read-only execution -> chart + summary. */
@Service
public class AssistantService {
    static final List<String> EXAMPLES = List.of(
            "How much did I spend on food last month vs this month?",
            "Show my spending by category this month",
            "What are my top 5 merchants this year?",
            "What is my monthly spending trend over the last 6 months?",
            "What were my 10 largest transactions last month?",
            "How much income did I receive this year?",
            "What is my average monthly spend on shopping?",
            "How much did I spend at Swiggy in the last 3 months?",
            "How am I doing against my budgets this month?",
            "How many transactions did I make last week?");

    private final LlmProvider llm;
    private final SqlValidator validator;
    private final ReadOnlyQueryExecutor executor;
    private final AssistantQueryRepository history;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    public AssistantService(LlmProvider llm, SqlValidator validator, ReadOnlyQueryExecutor executor,
                            AssistantQueryRepository history, AuditService audit, Clock clock, ZoneId appZone) {
        this.llm = llm;
        this.validator = validator;
        this.executor = executor;
        this.history = history;
        this.audit = audit;
        this.clock = clock;
        this.zone = appZone;
    }

    public record AnswerDto(String question, String sql, List<String> columns, List<List<Object>> rows, Chart chart,
                            String summary, String provider, long durationMs) {}

    public record HistoryDto(String question, String sql, int rowCount, Instant createdAt) {}

    public AnswerDto ask(AuthenticatedUser user, String question) {
        long start = System.nanoTime();
        String q = question.trim();
        String raw = llm.complete(new LlmRequest(LlmTask.TEXT_TO_SQL,
                SchemaPrompt.system(LocalDate.now(clock.withZone(zone))), "Question: " + q,
                Map.of("question", q), 800));
        String sql = cleanSql(raw);
        String executable;
        try {
            executable = validator.validate(sql);
        } catch (SqlValidator.UnsafeSqlException e) {
            audit.record(user.id(), user.email(), "NL_QUERY_REJECTED", "ASSISTANT", null, q + " :: " + e.getMessage());
            throw e;
        }
        QueryResult result = executor.run(user.id(), executable);
        Chart chart = ChartSuggester.suggest(result.columns(), result.rows());
        history.save(new AssistantQuery(user.id(), q, sql, result.rows().size(), llm.name()));
        audit.record(user.id(), user.email(), "NL_QUERY", "ASSISTANT", null, q);
        return new AnswerDto(q, sql, result.columns(), result.rows(), chart, summarise(result), llm.name(),
                (System.nanoTime() - start) / 1_000_000);
    }

    public List<HistoryDto> history(long userId) {
        return history.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(h -> new HistoryDto(h.getQuestion(), h.getSqlText(), h.getRowCount(), h.getCreatedAt()))
                .toList();
    }

    /** Strips markdown fences / leading labels that chat models sometimes add. */
    static String cleanSql(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
        }
        s = s.replaceFirst("(?i)^sql\\s*:\\s*", "").trim();
        return s;
    }

    static String summarise(QueryResult r) {
        if (r.rows().isEmpty()) {
            return "No matching transactions found.";
        }
        if (r.rows().size() == 1 && r.columns().size() == 1 && r.rows().getFirst().getFirst() instanceof Number n) {
            return label(r.columns().getFirst()) + ": " + money(n.doubleValue(), r.columns().getFirst());
        }
        if (r.columns().size() == 2 && r.rows().size() == 2 && r.rows().stream().allMatch(row -> row.get(1) instanceof Number)) {
            String aLabel = String.valueOf(r.rows().get(0).get(0));
            String bLabel = String.valueOf(r.rows().get(1).get(0));
            double a = ((Number) r.rows().get(0).get(1)).doubleValue();
            double b = ((Number) r.rows().get(1).get(1)).doubleValue();
            String change = a == 0 ? "" : String.format(Locale.ROOT, " (%+.0f%%)", (b - a) / a * 100);
            return aLabel + ": " + money(a, "total") + " vs " + bLabel + ": " + money(b, "total") + change;
        }
        return "Returned " + r.rows().size() + " row" + (r.rows().size() == 1 ? "" : "s") + ".";
    }

    private static String label(String column) {
        String s = column.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String money(double v, String column) {
        String c = column.toLowerCase(Locale.ROOT);
        boolean isCount = c.contains("count") || c.equals("transactions");
        return isCount ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "Rs %,.2f", v);
    }
}
