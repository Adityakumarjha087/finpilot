# FinPilot REST API (v1)

Interactive docs: `http://localhost:20801/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).
This file is the human-readable contract shared by the backend and the frontend.

* Base path: `/api/v1`. JSON everywhere except the two multipart import endpoints.
* Auth: `Authorization: Bearer <accessToken>` on everything except `/auth/register`, `/auth/login`,
  `/auth/login/mfa`, `/auth/refresh`, `/actuator/health`, Swagger.
* Money: plain JSON numbers, **negative = expense / outflow, positive = income**. Currency is INR.
* Dates: `YYYY-MM-DD`; timestamps ISO-8601 (`2026-11-03T14:22:00Z`); months `YYYY-MM`.
* Errors: RFC 7807 `application/problem+json`:
  `{ "type", "title", "status", "detail", "instance", "errors"?: { "<field>": "<message>" } }`.
  401 = missing/expired token (frontend should try `/auth/refresh` once, then log out), 403 = role,
  404, 409 = conflict, 422/400 = validation, 429 = rate limited (`Retry-After` header).
* Paging: `?page=0&size=25` -> `{ content: T[], page, size, totalElements, totalPages }`.

## Auth
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/register` | `{email, password, displayName}` (password >= 10 chars, letters+digits) | 201 `LoginResponse` |
| POST | `/auth/login` | `{email, password}` | `LoginResponse` (rate-limited: 5/min per IP+email) |
| POST | `/auth/login/mfa` | `{mfaToken, code}` | `LoginResponse` |
| POST | `/auth/refresh` | `{refreshToken}` | `LoginResponse` (refresh token is rotated) |
| POST | `/auth/logout` | `{refreshToken}` | 204 |
| GET | `/auth/me` | | `User` |
| POST | `/auth/mfa/setup` | | `{secret, otpauthUri}` |
| POST | `/auth/mfa/enable` | `{code}` | `User` |
| POST | `/auth/mfa/disable` | `{code}` | `User` |

`LoginResponse = { mfaRequired: boolean, mfaToken?: string, accessToken?: string, refreshToken?: string, expiresIn?: number /*seconds*/, user?: User }`
When `mfaRequired` is true only `mfaToken` is set; call `/auth/login/mfa` with a 6-digit TOTP code.

`User = { id: number, email, displayName, role: "USER" | "ADMIN", mfaEnabled: boolean, currency: "INR" }`

## Categories
`GET /categories` -> `string[]`: Food, Groceries, Rent, Travel, Transport, Shopping, Bills, Entertainment,
Health, Subscriptions, Education, Income, Transfers, Uncategorized.

## Transactions
`GET /transactions?page&size&sort=postedAt,desc&category=&q=&from=&to=&flagged=true` -> `Page<Transaction>`
(`sort` fields: `postedAt`, `amount`, `merchant`, `category`; `q` searches description/merchant).

```ts
Transaction = {
  id: number, postedAt: string, description: string, merchant: string, amount: number,
  category: string, categorySource: "ML" | "USER" | "RULE" | "NONE", categoryConfidence: number | null,
  notes: string | null,
  anomaly: { status: "NONE" | "FLAGGED" | "DISMISSED", score: number | null, reasons: string[] }
}
```
`PATCH /transactions/{id}` `{category?, notes?}` -> `Transaction`. Changing the category stores a user correction
(feeds the active-learning retrain loop and a per-merchant rule). `DELETE /transactions/{id}` -> 204.

## Imports (CSV / XLSX)
1. `POST /imports/preview` (multipart, field `file`, max 5 MB, `.csv` or `.xlsx`) ->
   `{ fileName, headers: string[], sampleRows: string[][] /*<=10*/, totalRows, suggestedMapping: ColumnMapping }`
2. `POST /imports` (multipart: `file` + `mapping` part as JSON string or `application/json`) ->
   `{ batchId, fileName, totalRows, imported, duplicates, failed, errors: {row, message}[] }`
3. `GET /imports` -> `{ id, fileName, importedAt, totalRows, imported, duplicates, failed }[]`

```ts
ColumnMapping = {
  date: string,            // header name of the date column (required)
  time?: string | null,    // optional time column
  description: string,     // narration / description column (required)
  amount?: string | null,  // single signed amount column, OR
  debit?: string | null,   // separate withdrawal column (positive numbers) and
  credit?: string | null,  // deposit column
  dateFormat?: string | null, // Java pattern e.g. "dd/MM/yyyy"; null = auto-detect
  invertSign?: boolean     // flip sign of `amount` (cards that export expenses as positive)
}
```
Re-importing the same file is idempotent: duplicates are counted, not inserted.

## Budgets
* `GET /budgets?month=YYYY-MM` -> `BudgetStatus[]` (default current month)
* `POST /budgets` `{category, monthlyLimit}` -> 201 `BudgetStatus` (409 if a budget exists for that category)
* `PUT /budgets/{id}` `{category, monthlyLimit}` -> `BudgetStatus`; `DELETE /budgets/{id}` -> 204
* `GET /budgets/alerts?month=` -> `{budgetId, category, alertLevel, percent, message}[]` (only WARNING/EXCEEDED)

`BudgetStatus = { id, category, monthlyLimit, spent /*positive*/, percent /*0..n, 0.8 = 80%*/, alertLevel: "OK" | "WARNING" | "EXCEEDED", month }`
WARNING at >= 80 %, EXCEEDED at >= 100 %.

## Dashboard
`GET /dashboard?months=12` ->
```ts
{ currency: "INR",
  totals: { income, expense /*positive*/, net, savingsRate /*0..1*/ },            // current month
  monthly: { month, income, expense, net }[],                                    // oldest -> newest
  categoryBreakdown: { category, amount /*positive*/ }[],                        // current month expenses
  netWorth: { month, value }[],                                                  // opening balance + cumulative net
  flaggedAnomalies: number, budgetAlerts: number, transactionCount: number }
```

## Forecast
`GET /forecast?horizon=3` ->
```ts
{ horizon, confidence /*0.8*/, generatedAt,
  categories: { category, method, history: {month, amount}[], forecast: {month, mean, lower, upper}[] }[],
  total: { history: {month, amount}[], forecast: {month, mean, lower, upper}[] } }
```

## Anomalies
* `POST /anomalies/scan` -> `{ scanned, flagged }`
* `GET /anomalies` -> `Transaction[]` (status FLAGGED, highest score first)
* `POST /anomalies/{transactionId}/dismiss` -> `Transaction`

## Recurring payments / subscriptions
`GET /recurring` -> `{ merchant, category, cadence: "WEEKLY"|"MONTHLY"|"QUARTERLY"|"YEARLY", averageAmount /*positive*/, lastCharged /*date*/, nextExpected /*date*/, occurrences, monthlyCost, subscription: boolean }[]`

## Savings goals
* `GET /goals` -> `Goal[]`; `POST /goals` `{name, targetAmount, savedAmount, targetDate}` -> 201 `Goal`
* `PUT /goals/{id}` (same body) -> `Goal`; `DELETE /goals/{id}` -> 204
* `GET /goals/{id}/suggestion` ->
  `{ goalId, requiredMonthly, averageMonthlySurplus, feasible: boolean, cuts: {category, currentMonthly, suggestedMonthly, saving}[], advice: string, provider: string }`

`Goal = { id, name, targetAmount, savedAmount, targetDate, progress /*0..1*/, monthsRemaining, requiredMonthly }`

## Natural-language assistant (text-to-SQL)
* `POST /assistant/query` `{question}` (<= 300 chars) ->
  `{ question, sql, columns: string[], rows: (string|number|null)[][], chart: { type: "bar"|"line"|"pie"|"table"|"number", x?: string, y?: string }, summary: string, provider: string, durationMs: number }`
  Unsafe or unsupported questions -> 422 problem with `detail` explaining why.
* `GET /assistant/examples` -> `string[]` suggested questions
* `GET /assistant/history` -> `{ question, sql, rowCount, createdAt }[]` (latest 20)

## Audit log & admin
* `GET /audit?page&size` -> `Page<{ id, action, entityType, entityId, ip, details, createdAt, userEmail }>`
  (ADMIN sees all users, USER only their own)
* `POST /admin/ml/retrain` (ADMIN) -> `{ modelVersion, corrections, holdoutAccuracy }`
* `GET /admin/ml/status` (ADMIN) -> `{ modelVersion, trainedAt, holdoutAccuracy, nSamples, nCorrections, pendingCorrections }`

## Demo accounts (seeded)
* `demo@finpilot.dev` / `DemoPass123!` (USER, 12 months of synthetic data)
* `admin@finpilot.dev` / `AdminPass123!` (ADMIN)
