# API documentation
Interactive OpenAPI: `GET /swagger-ui.html`, spec at `/v3/api-docs` (management API). All management endpoints are under `/api/v1`, JSON, `Authorization: Bearer <access token>` unless noted. Errors: `{ "code", "message", "correlationId", "details?" }`; the correlation ID is also in `X-Request-Id`. Lists return `{ items, page, size, totalItems, totalPages }` (`size ≤ 100`).

Roles: **A**=ADMIN, **M**=API_MANAGER, **D**=DEVELOPER.

## Auth & profile
| Method & path | Access | Notes |
|---|---|---|
| `POST /auth/register` | public | `{email, displayName, password(10–72)}` → 201, new users are `DEVELOPER`; `409 EMAIL_TAKEN` |
| `POST /auth/login` | public | `{email, password}` → `{accessToken, refreshToken, expiresIn, tokenType, user}`; `401 INVALID_CREDENTIALS`, `423 ACCOUNT_LOCKED`, `403 ACCOUNT_DISABLED` |
| `POST /auth/refresh` | public | `{refreshToken}`; rotates; reuse → `401 REFRESH_TOKEN_REUSED` and the whole family is revoked |
| `POST /auth/logout` | public | `{refreshToken}` → 204, revokes the family |
| `GET /me` | any | current profile |

## Users (A)
`GET /users?q&page&size` · `PUT /users/{id}` `{roles[], active}` (refuses removing the last active admin or self-deactivation).

## APIs
| | Access | |
|---|---|---|
| `GET /apis?q&status&environment&page&size` | A M D | developers see only ACTIVE APIs and no upstream URL/owner |
| `GET /apis/{id}` | A M D | |
| `POST /apis` | A M | creates INACTIVE. Body fields: `name, description, version, environment(DEV/STAGING/PROD), gatewayPath, upstreamUrl, methods[], authPolicy(API_KEY/JWT/NONE), rateLimitEnabled, rateLimitRequests, rateLimitWindowSeconds, rateLimitBurst, connectTimeoutMs(100–10000), responseTimeoutMs(100–60000), maxRequestBytes(0–52428800), allowedHeaders[], corsOrigins[]`. Errors: `DESTINATION_*` (400), `PATH_IN_USE`/`PATH_RESERVED`, `HEADER_RESERVED`, `CORS_INVALID` |
| `PUT /apis/{id}` | A, M(owner) | |
| `POST /apis/{id}/status` `{status: ACTIVE|INACTIVE}` | A, M(owner) | |
| `DELETE /apis/{id}` | A, M(owner) | archives (history kept, path freed) |

## API keys
`GET /api-keys?all=false` (own; `all=true` admin) · `POST /api-keys` `{name, description?, apiIds[], scopes[READ|WRITE], expiresInDays(1–365, default 90), quotaRequests?}` → `{key, rawKey}` (**only time the secret appears**) · `POST /api-keys/{id}/rotate` → `{key, rawKey}` (old key revoked immediately) · `POST /api-keys/{id}/revoke` → 204. Max 20 active keys per user (`409 KEY_LIMIT`). Other users' keys return `404`.

## Analytics (A M)
Common query params: `from`, `to` (ISO-8601 instants, default last 24 h, max range 90 d), `apiId`.
`GET /analytics/summary` · `/timeseries` · `/by-api` · `/status-codes` · `/methods` · `/quota?apiId&keyId` · `/logs?apiId&outcome&status&correlationId&page&size`.

## Security (A M)
`GET /security/events?severity&acknowledged&page&size` · `GET /security/events/{id}` · `GET /security/counts` · `POST /security/events/{id}/acknowledge` `{note?}`.

## Audit & system
`GET /audit?action&actor&outcome&from&to&page&size` (A) · `GET /system/info` (A M, read-only settings, no secrets) · `GET /actuator/health[/readiness|/liveness]` (public, no details).

## Internal (gateway → management; header `X-Gateway-Token`, blocked at nginx)
`GET /internal/v1/gateway/config` · `POST /internal/v1/gateway/ingest` `{gatewayInstance, events[]}` (≤ 1000 events/batch).

## Gateway
Public URL: `/gateway{gatewayPath}/{rest}`, e.g. `GET /gateway/catalog/products?limit=3` → `http://catalog-service:8081/products?limit=3`. Credentials: `X-API-Key: ask_<12 hex>_<secret>` (policy `API_KEY`) or `Authorization: Bearer <JWT>` (policy `JWT`). Scope `READ` allows GET/HEAD/OPTIONS, `WRITE` everything else.

| Status | `code` | Cause |
|---|---|---|
| 400 | `BAD_REQUEST` | forbidden path sequence |
| 401 | `INVALID_CREDENTIALS` | missing/invalid/revoked/expired credential (reason is internal only) |
| 403 | `FORBIDDEN` / `DESTINATION_BLOCKED` | key not allowed for this API/scope · upstream address forbidden |
| 404 | `NOT_FOUND` | no active API at this path |
| 405 | `METHOD_NOT_ALLOWED` | `Allow` header lists permitted methods |
| 413 | `PAYLOAD_TOO_LARGE` | body over `maxRequestBytes` |
| 429 | `RATE_LIMITED` | `Retry-After`, `X-RateLimit-Limit/Remaining` |
| 502 | `UPSTREAM_UNAVAILABLE` | connect failure |
| 503 | `RATE_LIMIT_UNAVAILABLE` / `CONFIG_STALE` | Redis unavailable (fail closed) / stale config guard |
| 504 | `UPSTREAM_TIMEOUT` | exceeded response timeout |

Example upstreams: catalog `GET /products[?category&limit]`, `GET /products/{id}`, `POST /products`, `GET /slow?ms=`, `GET /whoami`; orders `GET|POST /orders`, `GET /orders/{id}`.
