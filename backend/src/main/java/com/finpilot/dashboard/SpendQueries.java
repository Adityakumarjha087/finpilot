package com.finpilot.dashboard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Aggregation queries over a user's transactions (parameterised; runs on the primary pool). */
@Component
public class SpendQueries {
    private final JdbcTemplate jdbc;
    private final ZoneId zone;

    public SpendQueries(JdbcTemplate jdbc, ZoneId appZone) {
        this.jdbc = jdbc;
        this.zone = appZone;
    }

    public record MonthlyFlow(YearMonth month, BigDecimal income, BigDecimal expense) {}

    public record CategoryMonth(String category, YearMonth month, BigDecimal spent) {}

    private Timestamp start(YearMonth m) {
        return Timestamp.from(m.atDay(1).atStartOfDay(zone).toInstant());
    }

    /** Expense totals (positive) per category within one month, excluding non-spending categories. */
    public Map<String, BigDecimal> spendByCategory(long userId, YearMonth month) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT category, SUM(-amount) AS spent FROM transactions
                WHERE user_id = ? AND amount < 0 AND posted_at >= ? AND posted_at < ?
                  AND category NOT IN ('Income', 'Transfers')
                GROUP BY category ORDER BY spent DESC
                """, rs -> {
            out.put(rs.getString("category"), rs.getBigDecimal("spent"));
        }, userId, start(month), start(month.plusMonths(1)));
        return out;
    }

    /** Income / expense per month (expense positive; transfers excluded from both). */
    public List<MonthlyFlow> monthlyFlows(long userId, YearMonth from, YearMonth to) {
        Map<YearMonth, MonthlyFlow> byMonth = new LinkedHashMap<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            byMonth.put(m, new MonthlyFlow(m, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        jdbc.query("""
                SELECT to_char(posted_at AT TIME ZONE ?, 'YYYY-MM') AS month,
                       COALESCE(SUM(amount) FILTER (WHERE amount > 0), 0) AS income,
                       COALESCE(SUM(-amount) FILTER (WHERE amount < 0), 0) AS expense
                FROM transactions
                WHERE user_id = ? AND posted_at >= ? AND posted_at < ? AND category <> 'Transfers'
                GROUP BY 1
                """, rs -> {
            YearMonth m = YearMonth.parse(rs.getString("month"));
            byMonth.put(m, new MonthlyFlow(m, rs.getBigDecimal("income"), rs.getBigDecimal("expense")));
        }, zone.getId(), userId, start(from), start(to.plusMonths(1)));
        return List.copyOf(byMonth.values());
    }

    /** Net (all categories incl. transfers) per month, used for the net-worth curve. */
    public Map<YearMonth, BigDecimal> monthlyNet(long userId, YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> out = new LinkedHashMap<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            out.put(m, BigDecimal.ZERO);
        }
        jdbc.query("""
                SELECT to_char(posted_at AT TIME ZONE ?, 'YYYY-MM') AS month, SUM(amount) AS net
                FROM transactions WHERE user_id = ? AND posted_at >= ? AND posted_at < ? GROUP BY 1
                """, rs -> {
            out.put(YearMonth.parse(rs.getString("month")), rs.getBigDecimal("net"));
        }, zone.getId(), userId, start(from), start(to.plusMonths(1)));
        return out;
    }

    /** Sum of all amounts strictly before a month (opening position for the net-worth curve). */
    public BigDecimal netBefore(long userId, YearMonth month) {
        BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE user_id = ? "
                + "AND posted_at < ?", BigDecimal.class, userId, start(month));
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Expense per (category, month) for spending categories. */
    public List<CategoryMonth> spendByCategoryMonth(long userId, YearMonth from, YearMonth to) {
        return jdbc.query("""
                SELECT category, to_char(posted_at AT TIME ZONE ?, 'YYYY-MM') AS month, SUM(-amount) AS spent
                FROM transactions
                WHERE user_id = ? AND amount < 0 AND posted_at >= ? AND posted_at < ?
                  AND category NOT IN ('Income', 'Transfers')
                GROUP BY 1, 2 ORDER BY 1, 2
                """, (rs, i) -> new CategoryMonth(rs.getString("category"), YearMonth.parse(rs.getString("month")),
                rs.getBigDecimal("spent")), zone.getId(), userId, start(from), start(to.plusMonths(1)));
    }

    public Instant monthStart(YearMonth m) {
        return start(m).toInstant();
    }
}
