package com.finpilot.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpilot.assistant.ReadOnlyQueryExecutor.QueryResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantHelpersTest {
    @Test
    void cleanSqlStripsFencesAndLabels() {
        assertThat(AssistantService.cleanSql("```sql\nSELECT 1\n```")).isEqualTo("SELECT 1");
        assertThat(AssistantService.cleanSql("SQL: SELECT 2")).isEqualTo("SELECT 2");
        assertThat(AssistantService.cleanSql(null)).isEmpty();
    }

    @Test
    void summaries() {
        assertThat(AssistantService.summarise(new QueryResult(List.of("total"), List.of()))).contains("No matching");
        assertThat(AssistantService.summarise(new QueryResult(List.of("total"), List.of(List.of(1234.5)))))
                .isEqualTo("Total: Rs 1,234.50");
        assertThat(AssistantService.summarise(new QueryResult(List.of("transactions"), List.of(List.of(12L)))))
                .isEqualTo("Transactions: 12");
        assertThat(AssistantService.summarise(new QueryResult(List.of("period", "total"),
                List.of(List.of("Last month", 100.0), List.of("This month", 150.0)))))
                .isEqualTo("Last month: Rs 100.00 vs This month: Rs 150.00 (+50%)");
        assertThat(AssistantService.summarise(new QueryResult(List.of("a", "b", "c"), List.of(List.of(1, 2, 3)))))
                .isEqualTo("Returned 1 row.");
    }

    @Test
    void charts() {
        assertThat(ChartSuggester.suggest(List.of("total"), List.of(List.of(5.0))).type()).isEqualTo("number");
        assertThat(ChartSuggester.suggest(List.of("month", "total"), List.of(List.of("2026-01", 5.0))).type())
                .isEqualTo("line");
        assertThat(ChartSuggester.suggest(List.of("category", "total"), List.of(List.of("Food", 5.0))).type())
                .isEqualTo("pie");
        var bar = ChartSuggester.suggest(List.of("merchant", "total", "transactions"), List.of(List.of("X", 5.0, 2L)));
        assertThat(bar.type()).isEqualTo("bar");
        assertThat(bar.x()).isEqualTo("merchant");
        assertThat(bar.y()).isEqualTo("total");
        assertThat(ChartSuggester.suggest(List.of("a"), List.of()).type()).isEqualTo("table");
        assertThat(ChartSuggester.suggest(List.of("d", "m", "c", "a"), List.of(List.of("x", "y", "z", 1.0))).type())
                .isEqualTo("table");
    }

    @Test
    void normalisesJdbcValues() {
        assertThat(ReadOnlyQueryExecutor.normalise(new BigDecimal("1.50"))).isEqualTo(1.5);
        assertThat(ReadOnlyQueryExecutor.normalise(3)).isEqualTo(3L);
        assertThat(ReadOnlyQueryExecutor.normalise(2.5f)).isEqualTo(2.5);
        assertThat(ReadOnlyQueryExecutor.normalise(java.sql.Date.valueOf("2026-01-02"))).isEqualTo("2026-01-02");
        assertThat(ReadOnlyQueryExecutor.normalise(java.sql.Timestamp.from(java.time.Instant.EPOCH)))
                .isEqualTo("1970-01-01T00:00:00Z");
        assertThat(ReadOnlyQueryExecutor.normalise(true)).isEqualTo(true);
        assertThat(ReadOnlyQueryExecutor.normalise(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .isEqualTo("00000000-0000-0000-0000-000000000001");
    }
}
