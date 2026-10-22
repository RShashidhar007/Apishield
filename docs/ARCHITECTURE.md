# Architecture

## Components
| Component | Tech | Responsibility |
|---|---|---|
| Management API (`backend`) | Spring Boot 3.3.4, Java 21, JPA, Flyway, SQL Server | Users/auth, API registry, keys, analytics queries, security events, audit; publishes gateway config; ingests traffic |
| Gateway (`gateway`) | Spring Cloud Gateway 4.1 (Spring Cloud 2023.0.3), WebFlux/Netty, Redis | Route matching, credential validation, policy enforcement, rate limiting, forwarding, metrics emission |
| Frontend (`frontend`) | Angular 19 standalone, Three.js, SCSS | Command centre, registry, vault, portal |
| `common` | plain Java 21 | `DestinationPolicy` (SSRF), `ApiKeyCodec`, config/ingest contracts |
| nginx | | Serves the SPA; single origin for `/api` and `/gateway`; CSP; blocks `/internal` |
Management and gateway are separate deployables that share only `common` and three secrets (`JWT_SECRET`, `API_KEY_PEPPER`, `GATEWAY_TOKEN`).

## Gateway request lifecycle
1. `RequestCtxWebFilter` (every `/gateway/**` request): accept a safe `X-Request-Id` (`^[A-Za-z0-9._-]{8,64}$`) or generate one; resolve client IP (XFF only from trusted proxies); register a `beforeCommit` hook that emits exactly one `TrafficEvent` and strips infra response headers.
2. `DynamicRouteLocator` matches the longest public prefix (`/gateway` + `gatewayPath`) against the **current in-memory snapshot**; no match → `404 NOT_FOUND` (still recorded).
3. `PolicyFilter` (per route): stale-config guard (optional) → CORS preflight → **method allow-list** → path validation/rewrite (rejects `..`, `.`, `%2f`, `%5c`, `%00`, `//`, double encoding, control chars) → **credentials** (`Authenticator`) → **declared size** → **rate limit** (Redis) → request-header sanitation → streamed-size counter.
4. Spring Cloud Gateway's Netty routing filter forwards using a client whose **address resolver validates the host allow-list and every resolved IP** (`DestinationConfig`), with per-route connect/response timeouts from route metadata; redirects are not followed.
5. Response: hop-by-hop headers removed by SCG; `Server`/`X-Powered-By`/`Via` stripped; `X-Request-Id` and `X-RateLimit-*` added.
6. `GatewayErrorHandler` converts any failure into `{code,message,correlationId}` (no stack traces, hosts, IPs): `401/403/405/413/429/400`, `502` (connect failure), `504` (timeout), `403 DESTINATION_BLOCKED`, `503 RATE_LIMIT_UNAVAILABLE`, `500`.
Outcome codes recorded: `OK, UPSTREAM_ERROR, UPSTREAM_TIMEOUT, UPSTREAM_UNAVAILABLE, AUTH_FAILED, FORBIDDEN, RATE_LIMITED, RATE_LIMIT_UNAVAILABLE, METHOD_NOT_ALLOWED, PAYLOAD_TOO_LARGE, DESTINATION_BLOCKED, NOT_FOUND, BAD_REQUEST, INTERNAL_ERROR`.

## Header policy
Request → upstream is an **allow-list** (`accept*, cache-control, content-type, content-length, if-*, range, user-agent, idempotency-key, origin, x-request-id` + per-API extras). Never forwarded: `Host, Forwarded, X-Forwarded-*, X-Real-IP, X-API-Key, Cookie, Proxy-*, hop-by-hop`; `Authorization` is forwarded **only** for `NONE` APIs. Client-supplied `Forwarded`/`X-Forwarded-*` are stripped; SCG then adds values derived from the real peer. Per-API extras cannot re-enable reserved headers (validated at registration and at runtime).

## Configuration synchronisation
Pull model: the gateway polls `GET /internal/v1/gateway/config` every `CONFIG_REFRESH_MS` (5 s). The management API returns routes of ACTIVE APIs + key records (prefix, HMAC hash, scopes, expiry, revoked flag) and a content hash `version`. On a new version the gateway re-validates every route against the destination policy (invalid routes are dropped), atomically swaps the snapshot and publishes `RefreshRoutesEvent`.
*Consistency*: eventual; activation, deactivation, key revocation and policy changes reach every gateway within one refresh interval (+ processing). *Management API down*: the last good snapshot keeps serving (availability); `MAX_CONFIG_STALE_SECONDS > 0` makes protected routes fail closed (`503 CONFIG_STALE`) once the snapshot is older than that. *Cold start*: readiness is DOWN until the first snapshot loads. Requests never call the management API.

## Analytics flow
Requests enqueue a `TrafficEvent` (O(1), bounded queue of 20 000; drops are counted and exposed in `/actuator/health`). Every 5 s a batch (≤ 500) is POSTed to `/internal/v1/gateway/ingest` (at-most-once; a failed batch is re-queued once if capacity allows). The backend persists rows in `request_logs`, updates key `last_used_at`, and runs `ThreatDetector`. Dashboards aggregate with T-SQL queries; a daily job purges by retention. Nothing is synthetic — empty ranges render empty states.

## Frontend communication
Same-origin calls to `/api/v1/**` (bearer interceptor with single-flight refresh on 401) and `/gateway/**` (portal "Try it", no bearer unless the API's policy is JWT). Pages for roles the user lacks are hidden/guarded in the UI, but every permission is enforced by the backend (URL rules **and** `@PreAuthorize`).
