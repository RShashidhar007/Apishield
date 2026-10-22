# Deployment

## Docker Compose (development / demo)
```bash
cp .env.example .env && $EDITOR .env
docker compose up -d --build
docker compose ps            # all services should become healthy
docker compose logs -f gateway backend
```
Startup order (health-gated): `sqlserver` → `db-init` (one-shot, creates DB/login) → `backend` (Flyway migrates, then readiness UP) → `gateway` (readiness UP after first config snapshot; needs `redis` healthy) → `frontend`. `catalog-service`/`order-service` start independently.
Published ports (all bound to `127.0.0.1`, **dev only**): `4200` frontend, `8080` management API, `8090` gateway. SQL Server (1433), Redis (6379) and the example services are internal. Persistent volume: `sqldata`. Restart policy `unless-stopped`; `stop_grace_period 30s` with graceful Spring shutdown; Java containers run as UID 10001 with `no-new-privileges` and all capabilities dropped.

## Environment variables
| Variable | Used by | Default | Meaning |
|---|---|---|---|
| `MSSQL_SA_PASSWORD` | sqlserver, db-init | required | SA password (complexity rules apply) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | backend | compose-provided | JDBC settings; password required |
| `JWT_SECRET` | backend, gateway | required, ≥ 32 bytes | HS256 signing key (shared) |
| `JWT_ISSUER`, `JWT_AUDIENCE` | both | `apishield`, `apishield-clients` | validated on every token |
| `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL` | backend | `PT15M`, `P7D` | ISO-8601 durations |
| `API_KEY_PEPPER` | both | required, ≥ 32 chars | HMAC key for API-key hashes — changing it invalidates all keys |
| `GATEWAY_TOKEN` | both | required, ≥ 24 chars | gateway→management shared secret |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | backend | empty | creates first admin if none exists (password ≥ 10) |
| `ALLOWED_UPSTREAM_HOSTS` | both | `catalog-service,order-service` | comma list, supports `*.suffix` |
| `ALLOWED_UPSTREAM_PORTS` | both | any | optional port allow-list |
| `ALLOW_PRIVATE_UPSTREAMS` | both | `true` | allow RFC1918/ULA **for allow-listed hosts** |
| `ALLOW_LOOPBACK_UPSTREAMS` | both | `false` | allow loopback upstreams (local dev only) |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:4200` | management API CORS |
| `RETENTION_REQUEST_LOG_DAYS`, `RETENTION_SECURITY_EVENT_DAYS` | backend | 30, 180 | purge job |
| `MANAGEMENT_URL` | gateway | `http://localhost:8080` | |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | gateway, redis | `localhost`,`6379`,— | |
| `CONFIG_REFRESH_MS` | gateway | `5000` | config poll interval = revocation latency |
| `MAX_CONFIG_STALE_SECONDS` | gateway | `0` (off) | fail closed for protected routes when the snapshot is older than this |
| `RATE_LIMIT_REDIS_TIMEOUT_MS` | gateway | `200` | Redis call timeout (then fail closed) |
| `TRUSTED_PROXIES` | gateway | empty (compose: `172.16.0.0/12`) | peers allowed to set `X-Forwarded-For` |
| `PORT` | backend/gateway | 8080/8090 | |

## Health checks
Backend: `/actuator/health/readiness` (DB), Gateway: `/actuator/health/readiness` (config loaded) and `/actuator/health` (adds Redis; shows queue/drop counters only to operators with access to the port), examples: `/actuator/health`. Keep actuator ports private in production.

## Production considerations
- Terminate TLS in front of nginx/the gateway and add HSTS; restrict `/actuator`, Swagger and the backend port to the private network; **never** expose `/internal/**` (nginx returns 404).
- Run ≥ 2 gateway instances (Redis keeps limits consistent); use managed Redis with TLS + auth and a failover plan (Redis outage = 503 for rate-limited APIs by design).
- Use a managed SQL Server, TLS with a real certificate (remove `trustServerCertificate=true`), separate migration and runtime logins, backups, and an audit-log archival process.
- Replace the shared HS256 secret with asymmetric signing; rotate `API_KEY_PEPPER` only with a key re-issue plan.
- Store secrets in a secret manager, not `.env`; scan images; pin base-image digests.
- Re-evaluate `ALLOW_PRIVATE_UPSTREAMS`: prefer a dedicated upstream allow-list over broad private reachability; add network policies/egress firewalling so the gateway can reach only intended upstreams.
- Kubernetes is intentionally not included (not justified for this scope).
