-- Read-only surface for the natural-language (text-to-SQL) assistant.
-- The assistant connects as a separate login role that can ONLY select from these
-- views. Each view filters by the per-transaction setting `finpilot.user_id`, which
-- the backend sets with set_config(..., true) before running validated SQL, so a
-- generated query can never see another user's rows or any base table.

CREATE VIEW nl_transactions WITH (security_barrier = true) AS
SELECT t.id,
       t.posted_at,
       (t.posted_at AT TIME ZONE '${appTimezone}')::date AS posted_date,
       t.description,
       t.merchant,
       t.amount,
       abs(t.amount)                                      AS abs_amount,
       (t.amount < 0)                                     AS is_expense,
       t.category
FROM transactions t
WHERE t.user_id = nullif(current_setting('finpilot.user_id', true), '')::bigint;

CREATE VIEW nl_budgets WITH (security_barrier = true) AS
SELECT b.category, b.monthly_limit
FROM budgets b
WHERE b.user_id = nullif(current_setting('finpilot.user_id', true), '')::bigint;

CREATE VIEW nl_goals WITH (security_barrier = true) AS
SELECT g.name, g.target_amount, g.saved_amount, g.target_date
FROM goals g
WHERE g.user_id = nullif(current_setting('finpilot.user_id', true), '')::bigint;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${readonlyUser}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
                       '${readonlyUser}', '${readonlyPassword}');
    ELSE
        EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', '${readonlyUser}', '${readonlyPassword}');
    END IF;
END
$$;

ALTER ROLE ${readonlyUser} SET default_transaction_read_only = on;
ALTER ROLE ${readonlyUser} SET statement_timeout = '3s';
ALTER ROLE ${readonlyUser} SET timezone = '${appTimezone}';
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM ${readonlyUser};
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM ${readonlyUser};
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO ${readonlyUser};
GRANT SELECT ON nl_transactions, nl_budgets, nl_goals TO ${readonlyUser};
