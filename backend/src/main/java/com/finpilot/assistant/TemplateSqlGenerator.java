package com.finpilot.assistant;

import com.finpilot.config.AppProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic intent + slot-filling text-to-SQL used by the offline "fake" LLM provider. It understands
 * totals, period comparisons, category breakdowns, monthly trends, top merchants, largest transactions,
 * averages, counts, income, net savings, budgets and goals, with time ranges such as "this month",
 * "last 3 months", "in March" or "last year". Output targets the read-only {@code nl_*} views only.
 */
@Component
public class TemplateSqlGenerator {
    public record Range(String label, LocalDate start, LocalDate end) {
        String where() {
            return "posted_date >= DATE '" + start + "' AND posted_date < DATE '" + end + "'";
        }
    }

    private static final Map<String, String> CATEGORY_SYNONYMS = new LinkedHashMap<>();

    static {
        put("Food", "food", "dining", "restaurants?", "eating out", "takeaways?", "food delivery", "coffee");
        put("Groceries", "grocer(y|ies)", "supermarkets?");
        put("Rent", "rent", "housing");
        put("Travel", "travel", "flights?", "hotels?", "trips?", "vacations?", "holidays?");
        put("Transport", "transport(ation)?", "cabs?", "taxis?", "fuel", "petrol", "commut(e|ing)", "rides?");
        put("Shopping", "shopping", "clothes", "clothing", "electronics");
        put("Bills", "bills?", "utilit(y|ies)", "electricity", "internet", "phone bills?");
        put("Entertainment", "entertainment", "movies?", "games?", "gaming", "concerts?");
        put("Health", "health", "medical", "medicines?", "pharmacy", "doctors?", "gym", "fitness");
        put("Subscriptions", "subscriptions?", "streaming");
        put("Education", "education", "courses?", "books?", "tuition");
        put("Income", "income", "salary", "salaries", "earnings?", "earned", "earn");
        put("Transfers", "transfers?", "investments?", "invest(ed|ing)?", "sips?");
    }

    private static void put(String category, String... patterns) {
        CATEGORY_SYNONYMS.put(category, "\\b(" + String.join("|", patterns) + ")\\b");
    }

    private static final Pattern N_MONTHS = Pattern.compile("\\b(?:last|past|previous)\\s+(\\d{1,2})\\s+months?\\b");
    private static final Pattern N_DAYS = Pattern.compile("\\b(?:last|past|previous)\\s+(\\d{1,3})\\s+days?\\b");
    private static final Pattern TOP_N = Pattern.compile("\\btop\\s+(\\d{1,2})\\b|\\b(\\d{1,2})\\s+(?:largest|biggest)");
    private static final Pattern MERCHANT = Pattern.compile("\\b(?:at|from|with)\\s+([a-z0-9&.]+(?:\\s+[a-z0-9&.]+)?)");
    private static final Set<String> NOT_MERCHANT = Set.of("the", "my", "this", "last", "least", "most", "all",
            "each", "every", "a", "an", "home", "work", "night", "weekends", "weekend", "budget", "budgets");
    private static final String MONTH_NAMES = "(january|february|march|april|may|june|july|august|september|october"
            + "|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)";
    private static final Pattern NAMED_MONTH = Pattern.compile("\\b(?:in|for|during)?\\s*" + MONTH_NAMES
            + "(?:\\s+(\\d{4}))?\\b");

    private final Clock clock;
    private final ZoneId zone;

    public TemplateSqlGenerator(Clock clock, AppProperties props) {
        this.clock = clock;
        this.zone = ZoneId.of(props.timezone());
    }

    public String generate(String question) {
        if (question == null || question.isBlank()) {
            throw new UnsupportedQuestionException("Ask a question about your transactions");
        }
        String q = " " + question.toLowerCase(Locale.ROOT).replaceAll("[?!,]", " ").replaceAll("\\s+", " ") + " ";
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Range> ranges = ranges(q, today);
        Set<String> categories = categories(q);
        String merchant = merchant(q, categories);
        int topN = topN(q);

        if (q.contains("budget")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return """
                    SELECT b.category, b.monthly_limit, COALESCE(SUM(t.abs_amount), 0) AS spent
                    FROM nl_budgets b
                    LEFT JOIN nl_transactions t
                      ON t.category = b.category AND t.is_expense
                     AND t.%s
                    GROUP BY b.category, b.monthly_limit
                    ORDER BY spent DESC""".formatted(r.where());
        }
        if (q.matches(".*\\bgoals?\\b.*")) {
            return "SELECT name, target_amount, saved_amount, target_date\nFROM nl_goals\nORDER BY target_date";
        }

        boolean income = categories.contains("Income") || q.matches(".*\\b(income|earn|earned|salary)\\b.*");
        boolean comparison = ranges.size() >= 2
                || q.matches(".*\\b(vs|versus|compare|compared|comparison)\\b.*") && ranges.size() >= 2;
        String amountExpr = income ? "amount" : "abs_amount";
        List<String> filters = new ArrayList<>();
        if (income) {
            filters.add("NOT is_expense");
            categories.remove("Income");
            if (categories.isEmpty()) {
                filters.add("category = 'Income'");
            }
        } else if (!q.matches(".*\\b(net|save|saved|savings|left over)\\b.*")) {
            filters.add("is_expense");
        }
        if (!categories.isEmpty()) {
            filters.add(categories.size() == 1 ? "category = '" + categories.iterator().next() + "'"
                    : "category IN (" + String.join(", ", categories.stream().map(c -> "'" + c + "'").toList()) + ")");
        }
        if (merchant != null) {
            filters.add("merchant ILIKE '%" + merchant + "%'");
        }

        if (comparison) {
            List<String> parts = new ArrayList<>();
            for (Range r : ranges.subList(0, Math.min(ranges.size(), 4))) {
                parts.add("SELECT '" + r.label() + "' AS period, COALESCE(SUM(" + amountExpr + "), 0) AS total\n"
                        + "FROM nl_transactions\nWHERE " + where(filters, r));
            }
            return String.join("\nUNION ALL\n", parts);
        }
        if (q.matches(".*\\bmerchants?\\b.*") || q.matches(".*where .*(spend|spent).* most.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return "SELECT merchant, SUM(abs_amount) AS total, COUNT(*) AS transactions\nFROM nl_transactions\nWHERE "
                    + where(filters, r) + "\nGROUP BY merchant\nORDER BY total DESC\nLIMIT " + (topN > 0 ? topN : 10);
        }
        if (q.matches(".*\\b(largest|biggest|most expensive|highest|top)\\b.*\\b(transactions?|purchases?|expenses?|payments?|spends?)\\b.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return "SELECT posted_date, merchant, category, abs_amount AS amount\nFROM nl_transactions\nWHERE "
                    + where(filters, r) + "\nORDER BY abs_amount DESC\nLIMIT " + (topN > 0 ? topN : 10);
        }
        if (q.matches(".*\\b(by category|per category|each category|breakdown|categories|category wise|split)\\b.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return "SELECT category, SUM(" + amountExpr + ") AS total\nFROM nl_transactions\nWHERE "
                    + where(filters, r) + "\nGROUP BY category\nORDER BY total DESC";
        }
        if (q.matches(".*\\b(average|avg|typical|mean)\\b.*")) {
            Range r = ranges.isEmpty() ? lastMonths(today, 6) : ranges.getFirst();
            if (q.matches(".*\\b(monthly|per month|a month|each month)\\b.*")) {
                return "SELECT ROUND(AVG(month_total), 2) AS average_monthly\nFROM (\n  SELECT date_trunc('month', "
                        + "posted_date) AS month, SUM(" + amountExpr + ") AS month_total\n  FROM nl_transactions\n  WHERE "
                        + where(filters, r) + "\n  GROUP BY 1\n) m";
            }
            return "SELECT ROUND(AVG(" + amountExpr + "), 2) AS average_amount, COUNT(*) AS transactions\n"
                    + "FROM nl_transactions\nWHERE " + where(filters, r);
        }
        if (q.matches(".*\\b(per month|monthly|each month|by month|month by month|trend|over time)\\b.*")) {
            Range r = ranges.isEmpty() ? monthAligned(today, 12) : ranges.getFirst();
            return "SELECT to_char(date_trunc('month', posted_date), 'YYYY-MM') AS month, SUM(" + amountExpr
                    + ") AS total\nFROM nl_transactions\nWHERE " + where(filters, r) + "\nGROUP BY 1\nORDER BY 1";
        }
        if (q.matches(".*\\b(how many|number of|count)\\b.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return "SELECT COUNT(*) AS transactions\nFROM nl_transactions\nWHERE " + where(filters, r);
        }
        if (q.matches(".*\\b(net|save|saved|savings|left over)\\b.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            filters.add("category <> 'Transfers'");
            return "SELECT COALESCE(SUM(amount), 0) AS net_savings\nFROM nl_transactions\nWHERE " + where(filters, r);
        }
        if (income || q.matches(".*\\b(spend|spent|spending|cost|pay|paid|expenses?|how much|total)\\b.*")) {
            Range r = ranges.isEmpty() ? thisMonth(today) : ranges.getFirst();
            return "SELECT COALESCE(SUM(" + amountExpr + "), 0) AS total\nFROM nl_transactions\nWHERE "
                    + where(filters, r);
        }
        throw new UnsupportedQuestionException("I can answer questions about spending, income, categories, "
                + "merchants, trends, budgets and goals. Try one of the example questions.");
    }

    private static String where(List<String> filters, Range r) {
        List<String> all = new ArrayList<>(filters);
        all.add(r.where());
        return String.join("\n  AND ", all);
    }

    private static Set<String> categories(String q) {
        Set<String> found = new LinkedHashSet<>();
        CATEGORY_SYNONYMS.forEach((cat, regex) -> {
            if (Pattern.compile(regex).matcher(q).find()) {
                found.add(cat);
            }
        });
        if (found.contains("Groceries") && q.contains("food") && !q.matches(".*food\\s+(and|&)\\s+grocer.*")) {
            // "grocery food" -> groceries only
            found.remove("Food");
        }
        return found;
    }

    private static String merchant(String q, Set<String> categories) {
        Matcher m = MERCHANT.matcher(q);
        while (m.find()) {
            String candidate = m.group(1).trim();
            String first = candidate.split(" ")[0];
            if (NOT_MERCHANT.contains(first) || first.matches("\\d+") || isTimeWord(candidate)
                    || CATEGORY_SYNONYMS.values().stream().anyMatch(r -> Pattern.compile(r).matcher(candidate).find())) {
                continue;
            }
            String[] words = candidate.split(" ");
            String name = words.length > 1 && (isTimeWord(words[1]) || NOT_MERCHANT.contains(words[1])
                    || words[1].matches("in|on|for|during|vs|and|over|since")) ? words[0] : candidate;
            String clean = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9& .]", "");
            return clean.isBlank() ? null : clean;
        }
        return null;
    }

    private static boolean isTimeWord(String s) {
        return s.matches(".*\\b(today|yesterday|week|month|months|year|years|days?|" + MONTH_NAMES + ")\\b.*");
    }

    private static int topN(String q) {
        Matcher m = TOP_N.matcher(q);
        if (m.find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(2);
            return Math.min(Integer.parseInt(n), 50);
        }
        return 0;
    }

    /** All time ranges in order of appearance. */
    List<Range> ranges(String q, LocalDate today) {
        TreeMap<Integer, Range> found = new TreeMap<>();
        addAll(found, q, "\\b(this|current) month\\b", thisMonth(today));
        addAll(found, q, "\\b(last|previous|past) month\\b", new Range("Last month",
                today.withDayOfMonth(1).minusMonths(1), today.withDayOfMonth(1)));
        addAll(found, q, "\\b(this|current) year\\b|\\bso far this year\\b|\\bytd\\b",
                new Range("This year", today.withDayOfYear(1), today.plusDays(1)));
        addAll(found, q, "\\b(last|previous) year\\b", new Range("Last year",
                today.withDayOfYear(1).minusYears(1), today.withDayOfYear(1)));
        addAll(found, q, "\\b(this|current) week\\b", new Range("This week",
                today.with(DayOfWeek.MONDAY), today.plusDays(1)));
        addAll(found, q, "\\b(last|previous) week\\b", new Range("Last week",
                today.with(DayOfWeek.MONDAY).minusWeeks(1), today.with(DayOfWeek.MONDAY)));
        addAll(found, q, "\\btoday\\b", new Range("Today", today, today.plusDays(1)));
        addAll(found, q, "\\byesterday\\b", new Range("Yesterday", today.minusDays(1), today));
        Matcher nm = N_MONTHS.matcher(q);
        while (nm.find()) {
            int n = Math.max(1, Math.min(Integer.parseInt(nm.group(1)), 36));
            found.put(nm.start(), q.matches(".*\\b(per month|monthly|each month|by month|trend|over time)\\b.*")
                    ? monthAligned(today, n) : lastMonths(today, n));
        }
        Matcher nd = N_DAYS.matcher(q);
        while (nd.find()) {
            int n = Math.max(1, Math.min(Integer.parseInt(nd.group(1)), 730));
            found.put(nd.start(), new Range("Last " + n + " days", today.minusDays(n - 1L), today.plusDays(1)));
        }
        Matcher mm = NAMED_MONTH.matcher(q);
        while (mm.find()) {
            String name = mm.group(1);
            if (name.equals("may") && !q.matches(".*\\b(in|for|during) may\\b.*")) {
                continue; // "may" as a verb
            }
            Month month = parseMonth(name);
            int year = mm.group(2) != null ? Integer.parseInt(mm.group(2))
                    : (month.getValue() <= today.getMonthValue() ? today.getYear() : today.getYear() - 1);
            LocalDate start = LocalDate.of(year, month, 1);
            found.put(mm.start(1), new Range(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year, start,
                    start.plusMonths(1)));
        }
        return new ArrayList<>(found.values());
    }

    private static void addAll(TreeMap<Integer, Range> found, String q, String regex, Range r) {
        Matcher m = Pattern.compile(regex).matcher(q);
        if (m.find()) {
            found.put(m.start(), r);
        }
    }

    private static Month parseMonth(String name) {
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(Locale.ROOT).startsWith(name.substring(0, 3))) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static Range thisMonth(LocalDate today) {
        return new Range("This month", today.withDayOfMonth(1), today.plusDays(1));
    }

    static Range lastMonths(LocalDate today, int n) {
        return new Range("Last " + n + " months", today.minusMonths(n).plusDays(1), today.plusDays(1));
    }

    static Range monthAligned(LocalDate today, int n) {
        return new Range("Last " + n + " months", today.withDayOfMonth(1).minusMonths(n - 1L), today.plusDays(1));
    }
}
