package io.apishield.common.model;

import java.util.List;
import java.util.Set;

/** One active API as seen by the gateway. authPolicy: API_KEY | JWT | NONE. */
public record RouteConfig(
        long id, String name, String version, String environment,
        String gatewayPath,        // e.g. "/catalog" (public path is /gateway + gatewayPath)
        String upstreamUrl,        // e.g. "http://catalog-service:8081" (optionally with a base path)
        Set<String> methods,
        String authPolicy,
        boolean rateLimitEnabled, int rateLimitRequests, int rateLimitWindowSeconds, int rateLimitBurst,
        int connectTimeoutMs, int responseTimeoutMs,
        long maxRequestBytes,
        List<String> allowedHeaders,
        List<String> corsOrigins) {}
