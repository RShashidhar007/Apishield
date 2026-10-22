package io.apishield.gateway;

import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicit header policy.
 * Request -> upstream: ALLOW-LIST. Anything not on the list is dropped (including Cookie, Host, Forwarded, X-Forwarded-*, X-Real-IP,
 * X-API-Key and Authorization unless the API's policy is NONE). Gateway re-adds X-Request-Id; Spring Cloud Gateway adds the
 * Forwarded/X-Forwarded-* headers from the real peer address after client-supplied values were stripped here.
 * Response -> client: deny-list of infrastructure-revealing headers (hop-by-hop headers are removed by Spring Cloud Gateway).
 */
public final class HeaderPolicy {
    private HeaderPolicy() {}
    static final Set<String> DEFAULT_REQUEST_ALLOW = Set.of("accept", "accept-encoding", "accept-language", "cache-control", "content-type", "content-length",
            "if-match", "if-none-match", "if-modified-since", "range", "user-agent", "idempotency-key", "origin", "x-request-id");
    static final Set<String> NEVER_FORWARD = Set.of("host", "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port",
            "x-forwarded-prefix", "x-real-ip", "x-api-key", "connection", "keep-alive", "proxy-authorization", "proxy-authenticate", "te", "trailer", "transfer-encoding", "upgrade", "x-gateway-token");
    static final Set<String> RESPONSE_STRIP = Set.of("server", "x-powered-by", "x-application-context", "x-aspnet-version", "via");

    public static void sanitizeRequest(HttpHeaders headers, List<String> routeAllowed, boolean forwardAuthorization, String correlationId) {
        Set<String> extra = routeAllowed.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        for (String name : List.copyOf(headers.keySet())) {
            String n = name.toLowerCase(Locale.ROOT);
            boolean allowed = !NEVER_FORWARD.contains(n) && (DEFAULT_REQUEST_ALLOW.contains(n) || extra.contains(n) || (forwardAuthorization && n.equals("authorization")));
            if (!allowed) headers.remove(name);
        }
        headers.set("X-Request-Id", correlationId);
    }

    public static void sanitizeResponse(HttpHeaders headers, String correlationId) {
        for (String name : List.copyOf(headers.keySet())) if (RESPONSE_STRIP.contains(name.toLowerCase(Locale.ROOT))) headers.remove(name);
        headers.set("X-Request-Id", correlationId);
    }
}
