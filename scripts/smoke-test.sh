#!/usr/bin/env bash
# End-to-end smoke test against a RUNNING stack (docker compose up -d --build). Uses only local example services.
# Usage: ./scripts/smoke-test.sh         (reads BOOTSTRAP_ADMIN_* from .env or the environment)
set -uo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && set -a && . ./.env && set +a
API="${API_URL:-http://localhost:8080}"; GW="${GATEWAY_URL:-http://localhost:8090}"
: "${BOOTSTRAP_ADMIN_EMAIL:?}" "${BOOTSTRAP_ADMIN_PASSWORD:?}"
PASS=0; FAIL=0; SUF="$(date +%s)"
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1  ($2)"; FAIL=$((FAIL+1)); }
check(){ [ "$2" = "$3" ] && ok "$1" || bad "$1" "expected $3 got $2"; }
jget() { python3 -c "import sys,json; d=json.load(sys.stdin); print($1)" 2>/dev/null; }
code() { curl -s -o /dev/null -w '%{http_code}' --max-time 15 "$@"; }
wait_for() { local n=$1; shift; for _ in $(seq 1 "$n"); do "$@" && return 0; sleep 1; done; return 1; }
# poll until `curl <args>` returns the wanted HTTP status (re-evaluated on every attempt)
wait_code() { local n=$1 want=$2; shift 2; for _ in $(seq 1 "$n"); do [ "$(code "$@")" = "$want" ] && return 0; sleep 1; done; return 1; }

echo "== 1. Dependencies healthy"
wait_code 240 200 "$API/actuator/health/readiness" && ok "management API ready" || { bad "management API ready" "timeout"; exit 1; }
wait_code 120 200 "$GW/actuator/health/readiness" && ok "gateway ready (config loaded)" || bad "gateway ready" "timeout"

echo "== 2. Authenticate as the bootstrap administrator"
TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$BOOTSTRAP_ADMIN_EMAIL\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}" | jget "d['accessToken']")
[ -n "$TOKEN" ] && ok "login returned an access token" || { bad "login" "no token"; exit 1; }
AUTH=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
check "unauthenticated management call is rejected" "$(code "$API/api/v1/apis")" 401

echo "== 3. Register and activate upstream APIs"
mkapi() { # name path upstream methods rateLimitEnabled reqs timeoutMs maxBytes
  curl -s -X POST "$API/api/v1/apis" "${AUTH[@]}" -d "{\"name\":\"$1\",\"version\":\"v1\",\"environment\":\"DEV\",\"gatewayPath\":\"$2\",\"upstreamUrl\":\"$3\",\"methods\":$4,\"authPolicy\":\"API_KEY\",
   \"rateLimitEnabled\":$5,\"rateLimitRequests\":$6,\"rateLimitWindowSeconds\":60,\"rateLimitBurst\":0,\"connectTimeoutMs\":1000,\"responseTimeoutMs\":$7,\"maxRequestBytes\":$8,\"allowedHeaders\":[],\"corsOrigins\":[]}"; }
CAT=$(mkapi "Smoke Catalog $SUF" "/smoke-catalog-$SUF" "http://catalog-service:8081" '["GET","POST"]' false 60 1500 200 | jget "d['id']")
ORD=$(mkapi "Smoke Orders $SUF" "/smoke-orders-$SUF" "http://order-service:8082" '["GET"]' true 3 3000 1000 | jget "d['id']")
[ -n "$CAT" ] && [ -n "$ORD" ] && ok "two APIs registered (ids $CAT, $ORD)" || { bad "register APIs" "$CAT/$ORD"; exit 1; }
for id in $CAT $ORD; do curl -s -o /dev/null -X POST "$API/api/v1/apis/$id/status" "${AUTH[@]}" -d '{"status":"ACTIVE"}'; done
mkkey() { curl -s -X POST "$API/api/v1/api-keys" "${AUTH[@]}" -d "{\"name\":\"smoke $1\",\"apiIds\":[$1],\"scopes\":$2,\"expiresInDays\":1}"; }
CK=$(mkkey $CAT '["READ","WRITE"]'); CKEY=$(echo "$CK" | jget "d['rawKey']"); CKID=$(echo "$CK" | jget "d['key']['id']")
OK_=$(mkkey $ORD '["READ"]' | jget "d['rawKey']")
[ -n "$CKEY" ] && [ -n "$OK_" ] && ok "API keys issued (secret shown once)" || bad "API keys" "missing"
echo "$(curl -s "$API/api/v1/api-keys" "${AUTH[@]}")" | grep -q "$CKEY" && bad "raw key leaked in list" "found" || ok "raw key not present in key listing"

echo "== 4. Gateway forwards real traffic"
wait_code 40 200 "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: $CKEY" && ok "gateway picked up the new route and key" || bad "route propagation" "timeout (>40s)"
BODY=$(curl -s "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: $CKEY")
echo "$BODY" | grep -q "Titanium Keyboard" && ok "response body came from the real catalog upstream" || bad "upstream body" "$BODY"
echo "$(curl -s "$GW/gateway/smoke-catalog-$SUF/whoami" -H "X-API-Key: $CKEY" -H 'Cookie: s=1' -H 'X-Forwarded-For: 6.6.6.6')" | grep -qiE '"(x-api-key|cookie)"' && bad "credentials leaked to upstream" "x-api-key/cookie present" || ok "gateway credentials/cookies not forwarded upstream"

echo "== 5. Rejections"
for i in 1 2 3 4 5 6; do code "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: ask_000000000000_invalidinvalidinvalidinvalid" >/dev/null; done
check "invalid API key -> 401" "$(code "$GW/gateway/smoke-catalog-$SUF/products" -H 'X-API-Key: ask_000000000000_invalidinvalidinvalidinvalid')" 401
check "missing credentials -> 401" "$(code "$GW/gateway/smoke-catalog-$SUF/products")" 401
check "unsupported method (DELETE) -> 405" "$(code -X DELETE "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: $CKEY")" 405
check "key for another API -> 403" "$(code "$GW/gateway/smoke-orders-$SUF/orders" -H "X-API-Key: $CKEY")" 403
check "oversized body -> 413" "$(code -X POST "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: $CKEY" -H 'Content-Type: application/json' -d "$(python3 -c 'print("{\"x\":\"" + "a"*400 + "\"}")')")" 413
check "path traversal -> 400" "$(code --path-as-is "$GW/gateway/smoke-catalog-$SUF/../admin" -H "X-API-Key: $CKEY")" 400
check "upstream timeout -> 504" "$(code "$GW/gateway/smoke-catalog-$SUF/slow?ms=5000" -H "X-API-Key: $CKEY")" 504

echo "== 6. Rate limiting (Redis token bucket, 3 requests / 60 s)"
R=""; for i in 1 2 3 4 5; do R="$R $(code "$GW/gateway/smoke-orders-$SUF/orders" -H "X-API-Key: $OK_")"; done
check "first 3 allowed, then 429" "$(echo $R)" "200 200 200 429 429"
RETRY=$(curl -si "$GW/gateway/smoke-orders-$SUF/orders" -H "X-API-Key: $OK_" | tr -d '\r' | grep -i '^retry-after:' | awk '{print $2}')
[ -n "$RETRY" ] && ok "429 carries Retry-After ($RETRY s)" || bad "Retry-After" "missing"

echo "== 7. Prohibited upstream destinations are rejected at registration"
for u in "http://169.254.169.254/latest/meta-data" "http://localhost:8080" "http://10.0.0.5" "file:///etc/passwd"; do
  check "reject $u" "$(code -X POST "$API/api/v1/apis" "${AUTH[@]}" -d "{\"name\":\"evil\",\"version\":\"v1\",\"environment\":\"DEV\",\"gatewayPath\":\"/evil-$SUF\",\"upstreamUrl\":\"$u\",\"methods\":[\"GET\"],\"authPolicy\":\"API_KEY\",\"rateLimitEnabled\":false,\"rateLimitRequests\":1,\"rateLimitWindowSeconds\":1,\"rateLimitBurst\":0,\"connectTimeoutMs\":1000,\"responseTimeoutMs\":1000,\"maxRequestBytes\":0}")" 400
done

echo "== 8. Analytics, security events and audit reflect the traffic (gateway batches every ~5 s)"
getsum() { curl -s "$API/api/v1/analytics/summary?apiId=$ORD" "${AUTH[@]}" | jget "d['total']"; }
ge5() { [ "$(getsum)" -ge 5 ] 2>/dev/null; }
wait_for 30 ge5 && ok "analytics counted the order API requests (total $(getsum))" || bad "analytics total" "$(getsum)"
RL=$(curl -s "$API/api/v1/analytics/summary?apiId=$ORD" "${AUTH[@]}" | jget "d['rateLimited']"); [ "${RL:-0}" -ge 2 ] && ok "analytics recorded $RL rate-limited requests" || bad "rate-limited count" "$RL"
wait_for 30 sh -c "curl -s '$API/api/v1/security/events?size=50' -H 'Authorization: Bearer $TOKEN' | grep -q REPEATED_INVALID_API_KEY" && ok "security event raised for repeated invalid API keys" || bad "security event" "not found"
AUD=$(curl -s "$API/api/v1/audit?size=100" "${AUTH[@]}")
for a in API_CREATED API_KEY_CREATED API_ACTIVATED LOGIN; do echo "$AUD" | grep -q "\"$a\"" && ok "audit contains $a" || bad "audit $a" "missing"; done

echo "== 9. Revocation"
check "revoke key" "$(code -X POST "$API/api/v1/api-keys/$CKID/revoke" "${AUTH[@]}")" 204
wait_code 30 401 "$GW/gateway/smoke-catalog-$SUF/products" -H "X-API-Key: $CKEY" && ok "revoked key rejected by the gateway after config refresh" || bad "revocation propagation" "still accepted after 30s"

echo; echo "Result: $PASS passed, $FAIL failed"; [ "$FAIL" -eq 0 ]
