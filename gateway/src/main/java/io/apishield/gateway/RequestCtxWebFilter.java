package io.apishield.gateway;

import io.apishield.common.model.Outcomes;
import io.apishield.common.model.TrafficEvent;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/** Creates the request context and correlation id for every /gateway request and records exactly one traffic event when the response commits. */
@Component
public class RequestCtxWebFilter implements WebFilter, Ordered {
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private final TrafficShipper shipper; private final ClientIpResolver ips;
    public RequestCtxWebFilter(TrafficShipper shipper, ClientIpResolver ips) { this.shipper = shipper; this.ips = ips; }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    @Override
    public Mono<Void> filter(ServerWebExchange ex, WebFilterChain chain) {
        String path = ex.getRequest().getURI().getRawPath();
        if (!(path.equals("/gateway") || path.startsWith("/gateway/"))) return chain.filter(ex);
        String in = ex.getRequest().getHeaders().getFirst("X-Request-Id");
        String id = in != null && SAFE_ID.matcher(in).matches() ? in : UUID.randomUUID().toString();
        RequestCtx ctx = new RequestCtx(id);
        ctx.clientIp = ips.resolve(ex.getRequest());
        ex.getAttributes().put(RequestCtx.ATTR, ctx);
        ex.getResponse().beforeCommit(() -> {
            HeaderPolicy.sanitizeResponse(ex.getResponse().getHeaders(), id);
            var sc = ex.getResponse().getStatusCode();
            int status = sc == null ? 200 : sc.value();
            String outcome = ctx.outcome != null ? ctx.outcome : (status >= 500 ? Outcomes.UPSTREAM_ERROR : Outcomes.OK);
            long now = System.nanoTime();
            Long upstream = ctx.upstreamStartNanos > 0 ? (now - ctx.upstreamStartNanos) / 1_000_000 : null;
            String p = path.length() > 512 ? path.substring(0, 512) : path;
            shipper.offer(new TrafficEvent(ctx.startedAt, id, ctx.apiId, ctx.keyId, ctx.userId, ctx.clientIp, ex.getRequest().getMethod().name(), p,
                    status, (now - ctx.startNanos) / 1_000_000, upstream, ctx.requestBytes, outcome, ctx.reason));
            return Mono.empty();
        });
        return chain.filter(ex);
    }
}
