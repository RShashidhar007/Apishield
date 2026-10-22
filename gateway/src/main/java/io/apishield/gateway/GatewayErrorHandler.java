package io.apishield.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apishield.common.model.Outcomes;
import io.apishield.common.net.DestinationRejectedException;
import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Converts every failure on /gateway/** into a stable JSON error. Never leaks stack traces, hosts, IPs or exception messages from upstream/infrastructure. */
@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);
    private final ObjectMapper mapper;
    public GatewayErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    private record Mapped(HttpStatus status, String code, String message, String outcome, String reason) {}

    @Override
    public Mono<Void> handle(ServerWebExchange ex, Throwable t) {
        RequestCtx ctx = ex.getAttribute(RequestCtx.ATTR);
        if (ctx == null || ex.getResponse().isCommitted()) return Mono.error(t);
        Mapped m = map(t);
        if (ctx.outcome == null) { ctx.outcome = m.outcome(); ctx.reason = m.reason(); }
        if (m.status() == HttpStatus.INTERNAL_SERVER_ERROR) log.error("correlationId={} unhandled gateway error", ctx.correlationId, t);
        else log.info("correlationId={} api={} rejected status={} outcome={} reason={}", ctx.correlationId, ctx.apiId, m.status().value(), m.outcome(), m.reason());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", m.code()); body.put("message", m.message()); body.put("correlationId", ctx.correlationId);
        ex.getResponse().setStatusCode(m.status());
        ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            DataBuffer buf = ex.getResponse().bufferFactory().wrap(mapper.writeValueAsBytes(body));
            return ex.getResponse().writeWith(Mono.just(buf));
        } catch (Exception e) { return ex.getResponse().setComplete(); }
    }

    private Mapped map(Throwable t) {
        if (t instanceof GatewayException g) return new Mapped(g.status(), g.code(), g.getMessage(), g.outcome(), g.reason());
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof GatewayException g) return new Mapped(g.status(), g.code(), g.getMessage(), g.outcome(), g.reason());
            if (c instanceof DestinationRejectedException d) return new Mapped(HttpStatus.FORBIDDEN, "DESTINATION_BLOCKED", "The upstream destination is not permitted", Outcomes.DESTINATION_BLOCKED, d.code());
        }
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof TimeoutException || c instanceof io.netty.handler.timeout.TimeoutException || c instanceof ConnectTimeoutException)
                return new Mapped(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "The upstream service did not respond in time", Outcomes.UPSTREAM_TIMEOUT, c.getClass().getSimpleName());
        }
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof ConnectException || c instanceof UnknownHostException || c instanceof ClosedChannelException || c instanceof reactor.netty.http.client.PrematureCloseException)
                return new Mapped(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "The upstream service is unavailable", Outcomes.UPSTREAM_UNAVAILABLE, c.getClass().getSimpleName());
        }
        if (t instanceof ResponseStatusException r) {
            int s = r.getStatusCode().value();
            return switch (s) {
                case 404 -> new Mapped(HttpStatus.NOT_FOUND, "NOT_FOUND", "No API is configured for this path", Outcomes.NOT_FOUND, "NO_ROUTE");
                case 504 -> new Mapped(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "The upstream service did not respond in time", Outcomes.UPSTREAM_TIMEOUT, "RESPONSE_TIMEOUT");
                case 503 -> new Mapped(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", "The service is unavailable", Outcomes.UPSTREAM_UNAVAILABLE, "503");
                case 502 -> new Mapped(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "The upstream service is unavailable", Outcomes.UPSTREAM_UNAVAILABLE, "502");
                default -> new Mapped(HttpStatus.valueOf(s), "REQUEST_REJECTED", "The request could not be processed", Outcomes.BAD_REQUEST, "HTTP_" + s);
            };
        }
        return new Mapped(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An internal gateway error occurred", Outcomes.INTERNAL_ERROR, t.getClass().getSimpleName());
    }
}
