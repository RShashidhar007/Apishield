package io.apishield.common.model;

import java.time.Instant;

/** One gateway request. No bodies, no credentials, no query strings are ever recorded. */
public record TrafficEvent(
        Instant timestamp, String correlationId, Long apiId, Long keyId, Long userId,
        String clientIp, String method, String path, int status,
        long latencyMs, Long upstreamLatencyMs, long requestBytes,
        String outcome, String reason) {}
