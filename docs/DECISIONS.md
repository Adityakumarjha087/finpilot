# Engineering decisions & scope notes

## Architecture
* **Three services + two stores.** Spring Boot owns users, money and security; the Python service owns models
  only and is stateless apart from its model files. The backend talks to it over HTTP with a shared
  `X-Internal-Token`. Postgres is the system of record, Redis backs rate limiting.
* **Money is `NUMERIC(14,2)` / `BigDecimal` end to end** in the backend (import parsing, storage, budgets,
  goals, dashboard aggregates). Floats appear only where values leave the money domain: ML feature vectors,
  forecast outputs and the assistant's tabular results (rendered, never written back).

## Text-to-SQL safety (defence in depth)
1. The provider (fake template engine or a real LLM) only *proposes* SQL.
2. `SqlValidator`: single statement, parsed by JSqlParser as a `SELECT`, no comments / `;` / `$` / quoted
   identifiers, an allow-list of tables (`nl_transactions`, `nl_budgets`, `nl_goals`) and of functions,
   a deny-list of keywords (`set_config`, `pg_*`, `information_schema`, DDL/DML...), then wrapped with
   `LIMIT 500`.
3. Execution uses a **separate login role** (`finpilot_readonly`) that has `SELECT` only on the three views,
   `default_transaction_read_only=on` and `statement_timeout=3s`, inside a `READ ONLY` transaction.
4. The views filter on `current_setting('finpilot.user_id')`, bound by the backend with
   `SET LOCAL` (transaction-local). Even a query with no `WHERE` clause sees only the caller's rows. Migration V4
   revokes `set_config()` from `PUBLIC`, so a query running as the read-only role cannot re-bind the user id.
   Integration tests prove the role cannot read base tables, cannot write, and that users are isolated.

## Offline-first AI
* `LLM_PROVIDER=fake` (default) is a deterministic intent/slot-filling text-to-SQL engine plus templated savings
  advice, so the app, tests and CI need no API key. Its output still goes through steps 2-4 above.
* Real providers behind the same `LlmProvider` interface: Anthropic (official `anthropic-java` SDK, default
  model `claude-opus-5-5`), OpenAI, Gemini and Ollama (plain JSON HTTP). Each is tested against a local mock
  HTTP server; none was exercised against the live vendor APIs (no keys available).
* Numbers in the savings plan are computed deterministically in Java; the LLM only phrases advice. If the LLM
  call fails the deterministic template is used.

## ML
* **Categorisation:** TF-IDF (char 2-5 + word 1-2 n-grams) + Logistic Regression. Chosen over LightGBM because
  sparse text features + a linear model train in seconds, give calibrated-enough probabilities for the
  confidence badge and retrain cheaply in the active-learning loop.
* **Active learning:** a correction (1) immediately becomes a per-user merchant rule applied to past and future
  transactions, and (2) is stored as a labelled example; a scheduled job retrains the global model (synthetic
  base corpus + corrections weighted 8x) once `RETRAIN_MIN_CORRECTIONS` are pending; admins can force it.
* **Forecasting:** statsmodels ETS (additive error, damped additive trend) with analytical 80% prediction
  intervals; falls back to mean +/- z*std for < 6 months. Prophet was skipped (heavy build, little gain on
  12-18 monthly points). Honest result: on synthetic data ETS is on par with a naive 3-month mean
  (MAPE 0.21 vs 0.205) - the synthetic series have little trend; the interval coverage (78%) is close to the
  nominal 80%.
* **Anomalies:** Isolation Forest on log amount, robust per-category z-score, merchant frequency and time-of-day
  deviation. A transaction is shown only if the forest isolates it **and** at least one interpretable rule
  explains it, so every flag has a human-readable reason. `contamination=0.01` was tuned on seeds 2000-2019 and
  evaluated on unseen seeds 3000-3019.
* **Synthetic data only.** `finpilot_ml/synthetic.py` generates noisy Indian-bank-style narrations; the
  "unseen merchants" accuracy (merchant-level split) is reported next to the in-distribution number because the
  latter (99%) overstates real-world performance.

## Scope cuts (depth, not correctness)
* One currency (INR) and one time zone per deployment (`APP_TIMEZONE`).
* Net worth = opening balance + cumulative net cash flow (no asset/liability accounts).
* No email delivery: budget alerts (80% / 100%) are in-app (dashboard banner + alerts endpoint).
* The retrained model is global (shared across users); per-user personalisation comes from merchant rules.
* Description/merchant are stored in plaintext so they can be searched and aggregated; free-text notes and
  TOTP secrets are AES-256-GCM encrypted. See `SECURITY.md`.
* Refresh tokens are kept in `localStorage` by the SPA (access token in memory). An httpOnly-cookie flow would
  be stronger against XSS; the strict CSP is the mitigation here.
