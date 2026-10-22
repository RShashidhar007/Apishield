# Testing

## Commands
| Scope | Command | Needs |
|---|---|---|
| Everything Java | `mvn -B verify` | JDK 21, Maven, Docker (optional: Redis Testcontainers test is skipped without Docker) |
| One module | `mvn -B -pl common test` / `-pl backend -am test` / `-pl gateway -am test` | |
| Frontend unit | `cd frontend && npm ci && npm test` | Node 22 |
| Frontend build | `cd frontend && npx ng build` | |
| End-to-end | `docker compose up -d --build && ./scripts/smoke-test.sh` | Docker, `.env`, python3, curl |

## Test inventory
| File | Kind | Covers |
|---|---|---|
| `common/…/DestinationPolicyTest` (18) | unit | allow-list, wildcard look-alikes, schemes, user-info, ports, traversal; metadata/loopback/ULA/CGNAT/private rules; **DNS-rebinding simulation**; unresolvable hosts |
| `common/…/ApiKeyCodecTest` (4) | unit | generation, hash-only storage, constant-time match, redaction, malformed keys |
| `backend/…/JwtServiceTest` | unit | round-trip, expiry, tamper, wrong key, wrong audience, garbage |
| `backend/…/ApiKeyServiceTest` | unit (Mockito) | create/list masking/rotate/revoke/ownership/limit/inactive APIs |
| `backend/…/ApiServiceSsrfTest` | unit (Mockito) | metadata/loopback/private/non-http upstreams rejected before persistence |
| `backend/…/ThreatDetectorTest` | unit (Mockito) | thresholds, dedupe, critical destination events, no events on OK traffic |
| `backend/…/AuthFlowIT` | Spring + H2(MSSQL mode) | register/login/me, invalid creds, 401/403 matrix, lockout, refresh rotation + replay revocation, internal-endpoint token checks |
| `gateway/…/HeaderPolicyTest`, `ClientIpResolverTest` | unit | header allow/deny, XFF trust model |
| `gateway/…/GatewayRoutingTest` | Spring + in-process upstream | real forwarding, path rewrite, header filtering, 401/403/405/413/429/503/504/400/404, no leakage |
| `gateway/…/GatewayDestinationBlockedTest` | Spring | connect-time blocking of forbidden resolved address |
| `gateway/…/RedisRateLimiterTest` | Testcontainers Redis | capacity, Retry-After, **concurrency atomicity**, key isolation |
| `frontend/**/*.spec.ts` (31 tests, 7 suites) | Jest/jsdom | formatters, AuthService (token storage, single-flight refresh), guards, interceptor (bearer scope, refresh+retry, logout), API client contracts, login form validation, chart empty states/gauge |
| `scripts/smoke-test.sh` | e2e | steps 1–16 of the brief against the real stack (auth → register APIs → key → forward → 401/405/413/400/504/429 → SSRF rejection → analytics, security events, audit → revocation) |

## Results actually obtained (sandbox: JRE 21 without Maven/Docker, Node 22)
| Check | Result |
|---|---|
| `common` main sources compile (`javac` via the JRE's `jdk.compiler`) | ✅ |
| `common` tests 22/22 | ✅ run with a **throw-away JUnit-API stub + reflection runner** (JUnit/Maven unavailable) — equivalent logic, not the real JUnit engine |
| Frontend `jest --ci` | ✅ 7 suites, 31 tests pass |
| Frontend `ng build` (production) | ✅ initial bundle 317.82 kB (92.42 kB transfer); Three.js scene in a lazy chunk |
| `bash -n scripts/smoke-test.sh` | ✅ syntax only |
| Backend/gateway/examples compile & tests | ❌ **not run** (no Maven Central) |
| Redis/Testcontainers, SQL Server, Flyway, Compose, smoke test, GitHub Actions | ❌ **not run** |
| Browser rendering of the Three.js scene, accessibility audit, responsive checks | ❌ **not run** (no browser) |

## Gaps in coverage (honest)
No component tests for API registry/forms, key vault, dashboard loading states, analytics filtering or role-dependent controls (only the shared pieces above); no browser e2e (Playwright) tests; no SQL Server-backed integration tests (Testcontainers `mssql` would be the next step); no load/DoS tests; native T-SQL analytics queries are untested.
