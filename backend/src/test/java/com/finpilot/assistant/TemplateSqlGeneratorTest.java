package com.finpilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finpilot.security.SecurityPrimitivesTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateSqlGeneratorTest {
    // 2026-11-26 12:00 IST
    private final TemplateSqlGenerator gen = new TemplateSqlGenerator(
            Clock.fixed(Instant.parse("2026-11-26T06:30:00Z"), ZoneOffset.UTC),
            SecurityPrimitivesTest.props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
    private final SqlValidator validator = new SqlValidator();

    @Test
    void foodLastMonthVsThisMonth() {
        String sql = gen.generate("How much did I spend on food last month vs this month?");
        assertThat(sql).contains("UNION ALL").contains("'Last month' AS period").contains("'This month' AS period")
                .contains("category = 'Food'").contains("is_expense")
                .contains("posted_date >= DATE '2026-10-01' AND posted_date < DATE '2026-11-01'")
                .contains("posted_date >= DATE '2026-11-01' AND posted_date < DATE '2026-11-27'");
        assertThat(sql.indexOf("Last month")).isLessThan(sql.indexOf("This month"));
    }

    @Test
    void intents() {
        assertThat(gen.generate("What are my top 5 merchants this year?")).contains("GROUP BY merchant")
                .contains("LIMIT 5").contains("DATE '2026-01-01'");
        assertThat(gen.generate("Show my spending by category this month")).contains("GROUP BY category");
        assertThat(gen.generate("What is my monthly spending trend over the last 6 months?"))
                .contains("to_char(date_trunc('month', posted_date), 'YYYY-MM')").contains("DATE '2026-06-01'");
        assertThat(gen.generate("What were my 10 largest transactions last month?")).contains("ORDER BY abs_amount DESC")
                .contains("LIMIT 10");
        assertThat(gen.generate("How much income did I receive this year?")).contains("NOT is_expense")
                .contains("category = 'Income'").contains("SUM(amount)");
        assertThat(gen.generate("What is my average monthly spend on shopping?")).contains("AVG(month_total)")
                .contains("category = 'Shopping'");
        assertThat(gen.generate("average transaction on groceries")).contains("AVG(abs_amount)");
        assertThat(gen.generate("How much did I spend at Swiggy in the last 3 months?"))
                .contains("merchant ILIKE '%SWIGGY%'").contains("DATE '2026-08-27'");
        assertThat(gen.generate("How am I doing against my budgets this month?")).contains("nl_budgets");
        assertThat(gen.generate("show my goals")).contains("nl_goals");
        assertThat(gen.generate("How many transactions did I make last week?")).contains("COUNT(*)")
                .contains("DATE '2026-11-16'").contains("DATE '2026-11-23'");
        assertThat(gen.generate("how much did I save this month")).contains("net_savings").doesNotContain("is_expense");
        assertThat(gen.generate("spending on travel in March")).contains("DATE '2026-03-01'").contains("'Travel'");
        assertThat(gen.generate("spending on travel in december")).contains("DATE '2025-12-01'");
        assertThat(gen.generate("total spent on food and groceries last 30 days"))
                .contains("category IN ('Food', 'Groceries')").contains("DATE '2026-10-28'");
        assertThat(gen.generate("what did i spend yesterday")).contains("DATE '2026-11-25'");
        assertThat(gen.generate("compare rent last year and this year")).contains("'Last year'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How much did I spend on food last month vs this month?", "What are my top 5 merchants this year?",
            "Show my spending by category this month", "What is my monthly spending trend over the last 6 months?",
            "What were my 10 largest transactions last month?", "How much income did I receive this year?",
            "What is my average monthly spend on shopping?", "How much did I spend at Swiggy in the last 3 months?",
            "How am I doing against my budgets this month?", "How many transactions did I make last week?",
            "how much did I save this month", "show my goals", "spending today"})
    void everyTemplatePassesTheValidator(String question) {
        validator.validate(gen.generate(question));
    }

    @Test
    void unsupported() {
        assertThatThrownBy(() -> gen.generate("tell me a joke")).isInstanceOf(UnsupportedQuestionException.class);
        assertThatThrownBy(() -> gen.generate(" ")).isInstanceOf(UnsupportedQuestionException.class);
    }

    @Test
    void merchantInjectionIsSanitised() {
        String sql = gen.generate("how much did I spend at x';drop table users");
        assertThat(sql).doesNotContain("';");
        validator.validate(sql);
    }
}
