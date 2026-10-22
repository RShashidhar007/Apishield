package io.apishield.gateway;

import io.apishield.common.model.Outcomes;
import io.apishield.common.model.RouteConfig;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * The per-route policy pipeline. Order: staleness -> CORS preflight -> method -> credentials -> request size -> rate limit ->
 * header sanitation -> path rewrite -> forward (done by Spring Cloud Gateway's Netty routing filter using the validated resolver).
 */
public class PolicyFilter implements GatewayFilter {
    private static final Pattern DOUBLE_ENCODED = Pattern.compile("%[0-9a-fA-F]{2}");

    private final RouteConfig route; private final Authenticator auth; private final RateLimiter limiter;
    private final ConfigStore store; private final GatewayProperties props; private final ClientIpResolver ips;
    private final String publicPrefix; private final URI upstream; private final String upstreamBasePath;

    public PolicyFilter(RouteConfig route, Authenticator auth, RateLimiter limiter, ConfigStore store, GatewayProperties props, ClientIpResolver ips) {
        this.route = route; this.auth = auth; this.limiter = limiter; this.store = store; this.props = props; this.ips = ips;
        this.publicPrefix = "/gateway" + route.gatewayPath();
        this.upstream = URI.create(route.upstreamUrl());
        String bp = upstream.getRawPath() == null ? "" : upstream.getRawPath();
        this.upstreamBasePath = bp.endsWith("/") ? bp.substring(0, bp.length() - 1) : bp;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange ex, GatewayFilterChain chain) {
        return Mono.defer(() -> {
            RequestCtx ctx = ex.getRequiredAttribute(RequestCtx.ATTR);
            ctx.apiId = route.id();
            ServerHttpRequest req = ex.getRequest();
            String method = req.getMethod().name();
            HttpHeaders rh = ex.getResponse().getHeaders();

            if (props.getMaxStaleSeconds() > 0 && !"NONE".equals(route.authPolicy()) && store.staleSeconds() > props.getMaxStaleSeconds())
                throw new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_STALE", "Gateway configuration is stale", Outcomes.UPSTREAM_UNAVAILABLE, "CONFIG_STALE");

            String origin = req.getHeaders().getOrigin();
            boolean corsOk = origin != null && route.corsOrigins() != null && route.corsOrigins().contains(origin);
            if (req.getMethod() == HttpMethod.OPTIONS && corsOk && req.getHeaders().getFirst("Access-Control-Request-Method") != null) {
                ex.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                rh.set("Access-Control-Allow-Origin", origin); rh.add("Vary", "Origin");
                rh.set("Access-Control-Allow-Methods", String.join(", ", route.methods()));
                rh.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key, X-Request-Id");
                rh.set("Access-Control-Max-Age", "600");
                return ex.getResponse().setComplete();
            }

            if (!route.methods().contains(method)) {
                rh.set("Allow", String.join(", ", route.methods()));
                throw new GatewayException(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed for this API", Outcomes.METHOD_NOT_ALLOWED, method);
            }

            String rawPath = req.getURI().getRawPath();
            String upstreamPath = rewrite(rawPath);

            var snap = store.current();
            Authenticator.Principal p;
            p = auth.authenticate(route, req.getHeaders(), method, snap);
            ctx.keyId = p.keyId(); ctx.userId = p.userId();

            long declared = req.getHeaders().getContentLength();
            if (declared > route.maxRequestBytes()) throw tooLarge();
            ctx.requestBytes = Math.max(0, declared);

            if (corsOk) {
                rh.set("Access-Control-Allow-Origin", origin); rh.add("Vary", "Origin");
                rh.set("Access-Control-Expose-Headers", "X-Request-Id, X-RateLimit-Limit, X-RateLimit-Remaining, Retry-After");
            }

            return rateLimit(ex, ctx, p).then(Mono.defer(() -> {
                ServerHttpRequest mutated = req.mutate().path(upstreamPath)
                        .headers(h -> HeaderPolicy.sanitizeRequest(h, route.allowedHeaders() == null ? java.util.List.of() : route.allowedHeaders(), "NONE".equals(route.authPolicy()), ctx.correlationId))
                        .build();
                ServerHttpRequest limited = new ServerHttpRequestDecorator(mutated) {
                    @Override public Flux<DataBuffer> getBody() {
                        AtomicLong seen = new AtomicLong();
                        return super.getBody().handle((buf, sink) -> {
                            long n = seen.addAndGet(buf.readableByteCount());
                            ctx.requestBytes = n;
                            if (n > route.maxRequestBytes()) { DataBufferUtils.release(buf); sink.error(tooLarge()); } else sink.next(buf);
                        });
                    }
                };
                ctx.upstreamStartNanos = System.nanoTime();
                return chain.filter(ex.mutate().request(limited).build());
            }));
        });
    }

    private GatewayException tooLarge() {
        return new GatewayException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Request body exceeds the limit of " + route.maxRequestBytes() + " bytes", Outcomes.PAYLOAD_TOO_LARGE, "MAX_BYTES");
    }

    private Mono<Void> rateLimit(ServerWebExchange ex, RequestCtx ctx, Authenticator.Principal p) {
        if (!route.rateLimitEnabled()) return Mono.empty();
        int limit = p.quotaOverride() != null ? p.quotaOverride() : route.rateLimitRequests();
        int capacity = limit + Math.max(0, route.rateLimitBurst());
        double refillPerMs = limit / (route.rateLimitWindowSeconds() * 1000.0);
        String identity = p.keyId() != null ? "k:" + p.keyId() : p.userId() != null ? "u:" + p.userId() : "ip:" + ctx.clientIp;
        String redisKey = "apishield:rl:" + route.id() + ":" + identity;
        return limiter.check(redisKey, capacity, refillPerMs, limit)
                // Redis failure => fail CLOSED (documented). A security-critical limit is never silently bypassed.
                .onErrorMap(e -> !(e instanceof GatewayException), e -> new GatewayException(HttpStatus.SERVICE_UNAVAILABLE, "RATE_LIMIT_UNAVAILABLE",
                        "Rate limiting is temporarily unavailable", Outcomes.RATE_LIMIT_UNAVAILABLE, e.getClass().getSimpleName()))
                .flatMap(r -> {
                    HttpHeaders h = ex.getResponse().getHeaders();
                    h.set("X-RateLimit-Limit", String.valueOf(r.limit()));
                    h.set("X-RateLimit-Remaining", String.valueOf(Math.max(0, r.remaining())));
                    if (r.allowed()) return Mono.<Void>empty();
                    h.set("Retry-After", String.valueOf(Math.max(1, (r.retryAfterMs() + 999) / 1000)));
                    return Mono.<Void>error(new GatewayException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Rate limit exceeded", Outcomes.RATE_LIMITED, identity.substring(0, identity.indexOf(':'))));
                });
    }

    /** Validates the raw request path and maps /gateway{route}/x to {upstreamBase}/x. Rejects traversal and encoding tricks. */
    String rewrite(String rawPath) {
        String rem = rawPath.substring(publicPrefix.length());
        String lower = rem.toLowerCase(Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00") || rem.contains("\\") || rem.contains("//") || lower.contains("%25"))
            throw bad("PATH_FORBIDDEN_SEQUENCE");
        String decoded;
        try { decoded = UriUtils.decode(rem, StandardCharsets.UTF_8); } catch (IllegalArgumentException e) { throw bad("PATH_MALFORMED"); }
        for (char c : decoded.toCharArray()) if (c < 0x20 || c == 0x7f) throw bad("PATH_CONTROL_CHARACTER");
        if (DOUBLE_ENCODED.matcher(decoded).find()) throw bad("PATH_DOUBLE_ENCODED");
        for (String seg : decoded.split("/", -1)) if (seg.equals("..") || seg.equals(".")) throw bad("PATH_TRAVERSAL");
        String full = upstreamBasePath + rem;
        return full.isEmpty() ? "/" : full;
    }

    private static GatewayException bad(String reason) {
        return new GatewayException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request path is not allowed", Outcomes.BAD_REQUEST, reason);
    }
}
