# Security notes (OWASP Top 10 mapping)

| OWASP 2021 | Control in FinPilot | Verified by |
|---|---|---|
| A01 Broken access control | Every query is scoped by the JWT subject (`findByIdAndUserId`), admin routes need `ROLE_ADMIN`, assistant SQL runs through per-user views | `ImportAndTransactionsIntegrationTest` (IDOR returns 404), `AuthIntegrationTest.rbacAndSecurityHeaders`, `AssistantIntegrationTest.viewsAreScopedPerUserAtTheDatabaseLevel` |
| A02 Cryptographic failures | BCrypt(12) passwords, HS256 JWT (secret >= 32 bytes enforced at startup), refresh tokens stored as SHA-256 hashes, AES-256-GCM for notes and TOTP secrets | `SecurityPrimitivesTest`, `AuthIntegrationTest.totpTwoFactorFlow` (ciphertext at rest) |
| A03 Injection | JPA/JDBC parameters everywhere; text-to-SQL validator + read-only role + views; CSV values never interpolated | `SqlValidatorTest` (25 hostile queries), `AssistantServiceTest`, `AssistantIntegrationTest` |
| A04 Insecure design | Refresh-token rotation with reuse detection, TOTP with replay protection, account lockout, rate limits | `AuthIntegrationTest` |
| A05 Security misconfiguration | Strict CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, CORS allow-list, no stack traces in errors, actuator exposes health/info/metrics only, non-root containers | `AuthIntegrationTest.rbacAndSecurityHeaders` |
| A06 Vulnerable components | Pinned, current dependency versions; CI builds from lockfiles (`uv.lock`, `package-lock.json`) | CI |
| A07 Identification & authentication failures | 5 logins/min per IP+email (Redis), lockout after 5 failures for 15 min, generic error messages, constant-time dummy hash for unknown emails, 15-min access tokens | `AuthIntegrationTest.wrongPasswordLocksAccountAndLoginIsRateLimited` |
| A08 Software & data integrity | Flyway-versioned schema validated on start (`ddl-auto=validate`); ML service requires an internal token | `ContextSmokeIntegrationTest`, ML `test_token_required` |
| A09 Logging & monitoring | Structured ECS JSON logs, append-only `audit_events` (logins, MFA, imports, corrections, budget/goal changes, NL queries, retrains). Logs carry ids and actions, not amounts or descriptions | `FeaturesIntegrationTest.auditAndAdminRetrain` |
| A10 SSRF | No user-controlled outbound URLs; ML/LLM endpoints come from configuration only | n/a |

Additional:
* Upload limits: 5 MB per file, 20 000 rows, `.csv`/`.xlsx` only; filenames sanitised.
* API-wide budget of `RATE_LIMIT_API` requests/minute per user (or IP when anonymous).
* Secrets come only from the environment (`.env`, never committed). `infra/gen-env.sh` generates random values.
