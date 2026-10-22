# Security

## Authentication & sessions
- Passwords: BCrypt cost 12, 10–72 chars. Unknown accounts still pay one BCrypt comparison (timing equalisation). Generic `INVALID_CREDENTIALS` for unknown user and wrong password.
- Lockout: 5 failures → 15 min lock (configurable). *Trade-off:* an attacker can deliberately lock a known account; mitigate with an edge IP throttle.
- Access token: HS256 JWT, 15 min; signature, issuer, audience, expiry enforced (`JwtService`, tested for tamper/expiry/wrong key/audience). Secret ≥ 32 bytes, required from the environment; the app refuses to start otherwise. Every request re-checks the user in the DB so deactivation applies immediately.
- Refresh token: 256-bit random, stored as SHA-256, rotated on every use; reuse of a rotated token revokes the entire family. Logout revokes the family.
- **Browser storage decision:** access token in memory only; refresh token in `sessionStorage` (survives reload, dies with the tab). Risk: readable by injected script (XSS). Mitigations: strict CSP (`script-src 'self'`), no `bypassSecurityTrust*`, escaped highlighter + Angular sanitiser, short access TTL, rotation/replay detection. HttpOnly cookies would remove token theft by XSS but would require CSRF defences; the bearer design needs none (no ambient credentials) and CSRF is therefore disabled by design.
- Authorization: URL rules **and** `@PreAuthorize` (see matrix in `docs/API_DOCUMENTATION.md`). Ownership checks for API changes and keys; foreign keys return 404.

## API keys
`ask_<12 hex prefix>_<32 random bytes base64url>` from `SecureRandom`; stored: prefix + `HMAC-SHA256(pepper, key)` only; compared in constant time; the gateway always computes the hash before lookup so unknown-prefix and wrong-secret cost the same; raw key shown once, never logged (`Generated.toString` redacts), never in list responses; expiry (default 90 d, max 365), revoke, rotate (old key revoked immediately), per-key quota, scopes, API allow-list, 20 active keys/user. Revocation reaches the gateway within the config refresh interval. API keys and JWTs are separate, explicit per-API policies; a key is never accepted where the policy is `JWT` and vice-versa. Clients receive one generic 401 for any credential failure.

## SSRF, destinations and open-proxy prevention
- Upstreams are configured only by `ADMIN`/`API_MANAGER`, never by request data.
- `DestinationPolicy` (shared by both apps): http/https only, no user-info/query/fragment, **host must be on `ALLOWED_UPSTREAM_HOSTS`** (exact or `*.suffix`), optional port allow-list, base path may not contain `..`.
- Address rules, applied to **every** DNS answer: always block unspecified, multicast, link-local (169.254/16 incl. cloud metadata, `100.100.100.200`, `fd00:ec2::254`), 0/8, CGNAT 100.64/10, 192.0.0/24, benchmarking, ≥240/4; loopback blocked unless `ALLOW_LOOPBACK_UPSTREAMS`; RFC1918/ULA only for allow-listed hosts when `ALLOW_PRIVATE_UPSTREAMS` (needed for Docker service names).
- Checked (1) at registration (incl. DNS), (2) when the gateway loads config (URL + IP literals), (3) **at connection time** by a Netty address resolver that validates allow-list and addresses and returns the validated address to connect to — closing the DNS-rebinding window. Redirects are never followed. Tests: `DestinationPolicyTest` (22 cases incl. rebinding simulation, run), `GatewayDestinationBlockedTest` (needs Maven).
- The developer "Try it" console calls the same `/gateway` URLs from the browser: no server-side proxy exists, so it cannot bypass policy.

## Rate limiting
Token bucket in Redis (atomic Lua, Redis server clock). Identity: key id → user id → client IP. Per-API limit/window/burst, per-key override. `429` + `Retry-After`. **Redis failure ⇒ fail closed** (`503 RATE_LIMIT_UNAVAILABLE`, 200 ms timeout) — never silently bypassed. Client IP is taken from the TCP peer; `X-Forwarded-For` is honoured only from `TRUSTED_PROXIES` (right-most untrusted hop), and nginx overwrites XFF.

## Request handling hardening
Method allow-list; path traversal/encoding checks; header allow-list and reserved-header stripping (header injection/smuggling vectors such as `Transfer-Encoding`, `Host`, `Forwarded` never pass); declared and streamed body-size limits; connect/response timeouts; max request header size 16 KB; correlation-id sanitisation; JSON errors without stack traces; response `Server`/`X-Powered-By` stripped. Request smuggling is addressed by relying on Netty's HTTP/1.1 parsing and not forwarding `Transfer-Encoding`/`Connection`; behind a proxy keep both sides on consistent parsers.

## Threat detection
Threshold rules over persisted gateway outcomes (see `ThreatDetector`): repeated auth failures / invalid API keys (HIGH), excessive rate-limit hits (MEDIUM), repeated denied requests (MEDIUM), oversized requests (LOW), destination violations (CRITICAL, immediate). Thresholds in `apishield.detection.*`. Rule-based and explainable — **no ML/AI detection is claimed**. Bodies, headers, credentials and query strings are never stored.

## Secrets, logging, audit
All secrets via environment (`.env` is git-ignored; `.env.example` has placeholders). No default credentials: the first admin exists only if `BOOTSTRAP_ADMIN_*` is set. Raw keys/JWTs/passwords are never logged or audited. Audit entries are written in their own transaction (kept on failure/denial).

## Known limitations / residual risks
1. **Unverified**: Spring/Spring-Cloud-Gateway code has not been compiled or run (see status doc); treat all controls above as *designed and unit-tested where noted*, not production-proven.
2. Self-registration is open (creates DEVELOPERs only). Disable or gate it at the edge for private deployments; add email verification for public ones.
3. Gateway has no per-IP throttle on authentication failures (key brute force is infeasible at 256 bits, but failures still cost CPU); add an edge/WAF limit.
4. Response size is not capped; slow-loris style upstream/response abuse relies on timeouts.
5. Revocation/deactivation reach the gateway within `CONFIG_REFRESH_MS`; with an unreachable management API the last snapshot is served unless `MAX_CONFIG_STALE_SECONDS` is set.
6. Traffic events are at-most-once; queue overflow drops events (counted, visible in health).
7. `ddl-auto=none`; `db_owner` for the app login; no TLS/HSTS in the bundled nginx; no WAF; no dependency scan results recorded; no external penetration test.
8. Shared HS256 secret between management and gateway; a compromise of either exposes token forging. Prefer asymmetric signing (RS256/EdDSA) for multi-team production.
9. SQL Server and Redis in Compose are for development (no TLS to Redis, `trustServerCertificate=true`).
