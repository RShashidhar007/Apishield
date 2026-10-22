# Implementation status

_Last updated from the build sandbox. "Verified" means executed and observed passing here; everything else is source that has never been compiled or run._

## Legend
✅ implemented **and verified** · 🟡 implemented, **unverified** · ◐ partial · ❌ not implemented

## Completed source files and modules
| Module | Contents | Status |
|---|---|---|
| `pom.xml` | parent POM: Spring Boot 3.3.4 + Spring Cloud 2023.0.3 (compatible train), Java 21 | 🟡 |
| `common/` | `DestinationPolicy`, `DestinationRejectedException`, `ApiKeyCodec`, `RouteConfig/KeyConfig/GatewayConfig/TrafficEvent/IngestBatch/Outcomes` + 22 tests | ✅ compiled & tests pass (stub JUnit harness) |
| `backend/` (≈ 55 files) | config, security (JWT, gateway-token filter, URL+method authz), auth (register/login/refresh rotation/replay/logout/lockout), users, API registry (validation, SSRF checks, audit), API keys, analytics (T-SQL queries), threat detection, ingest, gateway config export, audit, retention job, system info, error handling, Flyway `V1__baseline.sql`, 5 test classes | 🟡 |
| `gateway/` (≈ 20 files) | dynamic routes, `PolicyFilter`, `Authenticator`, `RedisRateLimiter` (Lua), validating Netty resolver, header policy, client-IP trust, error handler, metrics shipper, config sync, health, 6 test classes | 🟡 |
| `examples/catalog-service`, `order-service` | real in-memory upstreams (incl. `/slow`, `/whoami`) | 🟡 |
| `frontend/` (37 TS files) | design system, auth+interceptor+guards, typed API client, 12 lazy pages, Three.js `ShieldScene` (+fallback, disposal, reduced-motion, visibility pause), charts, network map, radar, request inspector, toasts/confirm | ✅ production build passes · 31 Jest tests pass |
| `infra/`, `docker-compose.yml`, `.env.example` | Dockerfiles, nginx (CSP, `/internal` blocked), SQL bootstrap, Compose with health gating | 🟡 |
| `.github/workflows/ci.yml` | backend, frontend, dependency scan, compose smoke job | 🟡 never run |
| `scripts/smoke-test.sh` | 9-section e2e covering brief items 1–16 | 🟡 (`bash -n` ✅) |
| `docs/*`, `README.md` | all required documents | ✅ written |

## Build and test commands actually executed
| Command | Result |
|---|---|
| `javac` (JRE `jdk.compiler`) on `common/src/main` | ✅ compiles |
| `common` tests via stub JUnit API + reflection runner | ✅ passed=22 failed=0 |
| `npx @angular/cli@19 new frontend …`, `npm i three@0.169.0` | ✅ |
| `ng build --configuration development` / `ng build` (production) | ✅ initial 317.82 kB raw / 92.42 kB transfer |
| `npx jest --ci` | ✅ 7 suites, 31 tests |
| `bash -n scripts/smoke-test.sh` | ✅ |
**Not executable here:** `mvn …` (no Maven, Maven Central blocked), `docker …`, SQL Server, Redis, any browser.

## Implemented but unverified (highest risk first)
1. **Compilation of backend/gateway/examples.** Written carefully and reviewed, but a first `mvn verify` will likely surface small errors (generic inference in JPA `Specification` lambdas, Spring Cloud Gateway API details such as `Route.async()`/`RouteMetadataUtils`, Lombok on JDK 21). Fix as they appear — do not weaken tests.
2. **Gateway runtime behaviour**: per-route timeouts via route metadata, error mapping of Netty exceptions (cause-chain based), `beforeCommit` recording, the validating address resolver being invoked for hostnames (and the IP-literal case, mitigated at config sync), Lua script, chunked-body counting.
3. **T-SQL analytics** (`percentile_cont … over ()`, `dateadd/datediff` bucketing, `delete top`) and timestamp type conversion in `AnalyticsService.ts()`.
4. **Flyway migration** on SQL Server; JPA mapping parity (`ddl-auto=none`; enable `validate` after checking).
5. **Spring Security** URL/method rules and exception JSON; `AuthFlowIT` runs on H2, not SQL Server.
6. **Compose stack** (health checks, `db-init` quoting, `cap_drop` on Java images), nginx config, Dockerfiles, CI workflow, `smoke-test.sh` logic.
7. **Frontend in a real browser**: WebGL scene rendering/fallback, layout at desktop/tablet/mobile, keyboard flow, reduced-motion, contrast. Only build + unit tests were run; **no screenshots exist**.

## Known issues and incomplete features
- ❌ Developer access-request workflow, per-API "versions" as a separate entity (a version is a row), separate rate-limit/route tables (merged into `apis`).
- ◐ Frontend tests cover shared logic and login only; no tests for registry/key-vault/dashboard/analytics components or role-dependent controls; no Playwright e2e.
- ◐ Developers cannot see analytics (manager/admin only); developer usage is the per-key 24 h counter.
- ◐ Rate-limit meter shows persisted-traffic usage (ingest lag), not live Redis state.
- ◐ Dependency vulnerability scanning is configured in CI but has never been executed; `npm audit` not run here.
- ◐ Account preferences screen is limited to profile/sign-out; no password change or email verification.
- Limitations listed in `docs/SECURITY.md` (open registration, no response-size cap, at-most-once ingest, shared HS256, dev-grade DB/Redis TLS).

## Exact next steps (do in this order)
1. `cp .env.example .env`, fill secrets. `mvn -B -pl common test` (should mirror the 22 passing checks with real JUnit).
2. `mvn -B -pl backend -am test` → fix compile errors/tests; then `-pl gateway -am test` (Docker needed for the Redis test) → fix.
3. `docker compose up -d --build` → `docker compose ps` all healthy → check `docker compose logs backend` shows Flyway `V1` applied.
4. `./scripts/smoke-test.sh` → every line `PASS`; investigate any `FAIL` (most likely candidates: error-code mapping of timeouts/connect failures, T-SQL queries, header leakage assertion).
5. Open http://localhost:4200, walk each page; verify the 3D scene renders and the fallback appears with WebGL disabled; test keyboard-only and `prefers-reduced-motion`; take screenshots.
6. Switch `spring.jpa.hibernate.ddl-auto` to `validate`, resolve mapping differences.
7. Run the GitHub Actions workflow; fix findings from `npm audit`/OWASP.
8. Add the missing component tests, Testcontainers `mssql` integration tests, and a Playwright smoke test.
