package com.finpilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlValidatorTest {
    private final SqlValidator v = new SqlValidator();

    @Test
    void acceptsAndWrapsSafeSelect() {
        String out = v.validate("SELECT category, SUM(abs_amount) AS total FROM nl_transactions "
                + "WHERE is_expense AND posted_date >= DATE '2026-01-01' GROUP BY category ORDER BY total DESC;");
        assertThat(out).startsWith("SELECT * FROM (").endsWith("LIMIT 500").doesNotContain(";");
    }

    @Test
    void acceptsUnionJoinsAndAllowedFunctions() {
        v.validate("SELECT 'a' AS period, COALESCE(SUM(abs_amount), 0) AS total FROM nl_transactions "
                + "UNION ALL SELECT 'b', COALESCE(SUM(abs_amount), 0) FROM nl_transactions");
        v.validate("SELECT b.category, COUNT(t.id) FROM nl_budgets b LEFT JOIN nl_transactions t "
                + "ON t.category = b.category GROUP BY b.category");
        v.validate("SELECT to_char(date_trunc('month', posted_date), 'YYYY-MM') AS m, ROUND(AVG(amount), 2) "
                + "FROM nl_transactions WHERE category IN ('Food', 'Rent') AND merchant ILIKE '%SWIGGY%' GROUP BY 1");
        v.validate("SELECT name FROM nl_goals WHERE name = 'drop table; set'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM nl_transactions",
            "UPDATE nl_transactions SET amount = 0",
            "INSERT INTO nl_goals VALUES ('x', 1, 1, now())",
            "DROP TABLE transactions",
            "SELECT * FROM transactions",
            "SELECT * FROM users",
            "SELECT * FROM nl_transactions; DROP TABLE users",
            "SELECT * FROM nl_transactions -- comment",
            "SELECT * FROM nl_transactions /* x */",
            "SELECT pg_sleep(10) FROM nl_transactions",
            "SELECT set_config('finpilot.user_id', '2', false) FROM nl_transactions",
            "SELECT current_setting('finpilot.user_id') FROM nl_transactions",
            "SELECT * FROM pg_catalog.pg_tables",
            "SELECT * FROM information_schema.tables",
            "SELECT * INTO new_table FROM nl_transactions",
            "SELECT * FROM nl_transactions FOR UPDATE",
            "SELECT md5(description) FROM nl_transactions",
            "SELECT 1",
            "SELECT * FROM \"transactions\"",
            "SELECT $$x$$ FROM nl_transactions",
            "COPY nl_transactions TO '/tmp/x'",
            "SELECT * FROM (SELECT * FROM transactions) t",
            "WITH x AS (DELETE FROM transactions RETURNING *) SELECT * FROM x",
            "SELECT lo_import('/etc/passwd') FROM nl_transactions",
            "SELEC broken"})
    void rejectsUnsafe(String sql) {
        assertThatThrownBy(() -> v.validate(sql)).isInstanceOf(SqlValidator.UnsafeSqlException.class);
    }

    @Test
    void rejectsEmptyAndHuge() {
        assertThatThrownBy(() -> v.validate(" ")).isInstanceOf(SqlValidator.UnsafeSqlException.class);
        assertThatThrownBy(() -> v.validate(null)).isInstanceOf(SqlValidator.UnsafeSqlException.class);
        assertThatThrownBy(() -> v.validate("SELECT 1 FROM nl_goals WHERE name = '" + "x".repeat(5000) + "'"))
                .hasMessageContaining("too long");
    }
}
