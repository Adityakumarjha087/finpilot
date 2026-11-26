package com.finpilot.assistant;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Defence-in-depth gate for LLM-generated SQL (the read-only DB role and per-user views are the other layers).
 * Accepts exactly one SELECT (UNION/CTE allowed) that reads only the {@code nl_*} views, calls only
 * allow-listed functions, contains no comments, and wraps it with a hard row limit.
 */
@Component
public class SqlValidator {
    public static final int MAX_ROWS = 500;
    static final Set<String> ALLOWED_TABLES = Set.of("nl_transactions", "nl_budgets", "nl_goals");
    static final Set<String> ALLOWED_FUNCTIONS = Set.of("sum", "count", "avg", "min", "max", "coalesce", "nullif",
            "round", "abs", "lower", "upper", "trim", "length", "date_trunc", "date_part", "extract", "to_char",
            "cast", "greatest", "least", "concat", "substring", "stddev", "ceil", "floor", "string_agg", "row_number",
            "rank", "dense_rank", "lag", "lead", "percentile_cont", "make_date", "age", "now");
    private static final Set<String> KEYWORDS_BEFORE_PAREN = Set.of("in", "exists", "any", "all", "as", "over",
            "values", "filter", "from", "join", "and", "or", "not", "where", "on", "select", "then", "else", "when",
            "by", "using", "with", "union", "intersect", "except", "having", "case", "is", "between", "like",
            "ilike", "interval", "date", "within", "group", "lateral", "distinct", "limit", "offset", "end", "null");
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(insert|update|delete|merge|drop|alter|create|"
            + "truncate|grant|revoke|copy|into|lock|set|reset|execute|call|do|listen|notify|vacuum|analyze|explain|"
            + "prepare|deallocate|discard|refresh|security|pg_[a-z_]*|information_schema|current_setting|set_config|"
            + "dblink|lo_[a-z_]*)\\b");
    private static final Pattern FUNCTION_CALL = Pattern.compile("([a-z_][a-z0-9_.]*)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern QUOTED_IDENT = Pattern.compile("\"(?:[^\"]|\"\")*\"");

    public static class UnsafeSqlException extends com.finpilot.common.ApiException {
        public UnsafeSqlException(String reason) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, "Generated SQL was rejected by the safety validator: " + reason);
        }
    }

    /** Returns the executable, row-limited SQL or throws {@link UnsafeSqlException}. */
    public String validate(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new UnsafeSqlException("empty query");
        }
        String sql = rawSql.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.length() > 4000) {
            throw new UnsafeSqlException("query too long");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            throw new UnsafeSqlException("comments are not allowed");
        }
        String stripped = STRING_LITERAL.matcher(sql).replaceAll("''").toLowerCase(Locale.ROOT);
        if (stripped.contains(";")) {
            throw new UnsafeSqlException("only a single statement is allowed");
        }
        if (stripped.contains("$")) {
            throw new UnsafeSqlException("dollar quoting and parameters are not allowed");
        }
        if (QUOTED_IDENT.matcher(stripped).find()) {
            throw new UnsafeSqlException("quoted identifiers are not allowed");
        }
        Matcher forbidden = FORBIDDEN.matcher(stripped);
        if (forbidden.find()) {
            throw new UnsafeSqlException("'" + forbidden.group(1) + "' is not allowed");
        }
        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                throw new UnsafeSqlException("only a single statement is allowed");
            }
            statement = statements.get(0);
        } catch (JSQLParserException e) {
            throw new UnsafeSqlException("could not parse SQL");
        }
        if (!(statement instanceof Select)) {
            throw new UnsafeSqlException("only SELECT queries are allowed");
        }
        Set<String> tables = new TablesNamesFinder<Void>().getTables(statement);
        if (tables.isEmpty()) {
            throw new UnsafeSqlException("the query must read from nl_transactions, nl_budgets or nl_goals");
        }
        for (String table : tables) {
            String name = table.toLowerCase(Locale.ROOT).replace("public.", "");
            if (!ALLOWED_TABLES.contains(name)) {
                throw new UnsafeSqlException("table '" + table + "' is not accessible");
            }
        }
        Matcher fn = FUNCTION_CALL.matcher(stripped);
        while (fn.find()) {
            String name = fn.group(1);
            if (KEYWORDS_BEFORE_PAREN.contains(name)) {
                continue;
            }
            if (!ALLOWED_FUNCTIONS.contains(name)) {
                throw new UnsafeSqlException("function '" + name + "' is not allowed");
            }
        }
        return "SELECT * FROM (\n" + sql + "\n) AS fp_result LIMIT " + MAX_ROWS;
    }
}
