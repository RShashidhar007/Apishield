# Database design (SQL Server 2022)
Single Flyway migration `V1__baseline.sql`. Timestamps are `DATETIME2(3)` in **UTC** (Hibernate `jdbc.time_zone=UTC`, `Instant` mapped to `TIMESTAMP`, not `DATETIMEOFFSET`). All IDs are `BIGINT IDENTITY` (roles: `INT`).

| Table | Purpose / key constraints |
|---|---|
| `roles`, `user_roles` | seeded `ADMIN, API_MANAGER, DEVELOPER`; M:N with `users` (cascade delete on user) |
| `users` | unique `email`; `password_hash` (BCrypt), `active`, `failed_attempts`, `locked_until`, `last_login_at` |
| `refresh_tokens` | `token_hash` (SHA-256, unique), `family_id` (rotation chain), `used`, `revoked`, `expires_at` |
| `apis` | one row = one API **version** + its route + policies. Unique `(name, version, environment)`; **filtered unique index** `gateway_path WHERE status <> 'ARCHIVED'`; FK `owner_id`. Contains auth policy, rate-limit (requests/window/burst), timeouts, max bytes, allowed headers, CORS origins |
| `api_keys` | unique 12-char `prefix`, `key_hash` (HMAC-SHA256), `owner_id`, `expires_at`, `revoked`, `quota_requests`, `last_used_at`, `rotated_from` |
| `api_key_apis`, `api_key_scopes` | key → permitted APIs (FK) and scopes (`READ`/`WRITE`) |
| `request_logs` | high volume; indexes on `ts` (INCLUDE api/status/latency/outcome), `(api_id, ts)`, `(client_ip, outcome, ts)`, `correlation_id`, `(key_id, ts)` |
| `security_events` | type, severity, api, client IP, correlation id, occurrences, acknowledgement (+ note); dedupe index `(type, client_ip, api_id, created_at)` |
| `audit_logs` | ts, actor id/email, action, resource, outcome, detail (≤ 500 chars, never secrets), correlation id |

**Deliberate deviations from the brief:** route configuration, API version and rate-limit policy are columns of `apis` rather than separate tables (one version = one row keeps the gateway snapshot trivially consistent); there is no developer-access-request table (not implemented).
**Integrity:** FKs everywhere; transactions around multi-row writes; Spring Data/JPA parameter binding only (no string-built SQL — the only dynamic SQL is fixed T-SQL text with named parameters); DTOs only at the API boundary; pagination (`size ≤ 100`) on every list endpoint; analytics ranges capped at 90 days.
**Retention:** `request_logs` 30 d, `security_events` 180 d, expired refresh tokens 1 d after expiry (daily 03:15 job, batched deletes of 5 000 rows). `audit_logs` are never auto-purged — archive them with an operator process that satisfies your compliance period.
**Migrations:** forward-only; `flyway.clean-disabled=true`; the app never resets or drops data. `ddl-auto=none` (Hibernate validation is off until mapping parity is verified on SQL Server).
**Bootstrap:** `infra/sql/init.sql` creates the DB and an `apishield_app` login with `db_owner` on `apishield` only (needed for Flyway DDL). For stricter production, run Flyway with a migration login and run the app with DML-only rights.
