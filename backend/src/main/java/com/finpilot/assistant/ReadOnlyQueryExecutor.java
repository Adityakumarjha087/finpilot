package com.finpilot.assistant;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs validated SQL on the restricted read-only pool inside a READ ONLY transaction, after binding the
 * caller's id with {@code set_config('finpilot.user_id', ?, true)} (transaction-local) so the nl_* views
 * only expose that user's rows.
 */
@Component
public class ReadOnlyQueryExecutor {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ReadOnlyQueryExecutor(@Qualifier("readonly") JdbcTemplate jdbc,
                                 @Qualifier("readonly") TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public record QueryResult(List<String> columns, List<List<Object>> rows) {}

    public QueryResult run(long userId, String sql) {
        try {
            return tx.execute(status -> {
                jdbc.queryForObject("SELECT set_config('finpilot.user_id', ?, true)", String.class,
                        Long.toString(userId));
                return jdbc.query(sql, rs -> {
                    ResultSetMetaData md = rs.getMetaData();
                    List<String> cols = new ArrayList<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        cols.add(md.getColumnLabel(i));
                    }
                    List<List<Object>> rows = new ArrayList<>();
                    while (rs.next() && rows.size() < SqlValidator.MAX_ROWS) {
                        List<Object> row = new ArrayList<>(cols.size());
                        for (int i = 1; i <= cols.size(); i++) {
                            row.add(normalise(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return new QueryResult(cols, rows);
                });
            });
        } catch (DataAccessException e) {
            String msg = e.getMostSpecificCause().getMessage();
            throw new com.finpilot.common.ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The generated query could not be executed: " + (msg == null ? "database error"
                            : msg.lines().findFirst().orElse("database error")));
        }
    }

    static Object normalise(Object v) {
        if (v == null || v instanceof String || v instanceof Boolean) {
            return v;
        }
        if (v instanceof BigDecimal bd) {
            return bd.doubleValue();
        }
        if (v instanceof Double || v instanceof Float) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().toString();
        }
        return v.toString();
    }
}
