package com.finpilot.assistant;

import java.util.List;
import java.util.Locale;

/** Picks a sensible visualisation for a tabular result. */
public final class ChartSuggester {
    public record Chart(String type, String x, String y) {}

    private ChartSuggester() {}

    public static Chart suggest(List<String> columns, List<List<Object>> rows) {
        if (rows.isEmpty() || columns.isEmpty()) {
            return new Chart("table", null, null);
        }
        if (rows.size() == 1 && columns.size() == 1 && rows.getFirst().getFirst() instanceof Number) {
            return new Chart("number", null, columns.getFirst());
        }
        if (columns.size() >= 2 && rows.stream().allMatch(r -> r.get(0) instanceof String)) {
            int yIdx = -1;
            for (int i = 1; i < columns.size(); i++) {
                final int idx = i;
                if (rows.stream().allMatch(r -> r.get(idx) == null || r.get(idx) instanceof Number)) {
                    yIdx = i;
                    break;
                }
            }
            if (yIdx > 0 && columns.size() <= 3) {
                String x = columns.getFirst();
                String first = String.valueOf(rows.getFirst().getFirst());
                String xl = x.toLowerCase(Locale.ROOT);
                if (xl.contains("month") || xl.contains("date") || first.matches("\\d{4}-\\d{2}(-\\d{2})?")) {
                    return new Chart("line", x, columns.get(yIdx));
                }
                if (xl.equals("category") && rows.size() <= 8) {
                    return new Chart("pie", x, columns.get(yIdx));
                }
                return new Chart("bar", x, columns.get(yIdx));
            }
        }
        return new Chart("table", null, null);
    }
}
