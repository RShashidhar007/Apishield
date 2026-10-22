package io.apishield.backend.analytics;

import io.apishield.common.model.TrafficEvent;

import java.time.Instant;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}
    public record Summary(Instant from, Instant to, long total, long success, long errors, double errorRate, double avgLatencyMs, double p95LatencyMs,
                          double avgUpstreamLatencyMs, long rateLimited, long authFailures, long policyRejections, long upstreamFailures) {}
    public record TimePoint(Instant ts, long total, long errors, double avgLatencyMs, long maxLatencyMs) {}
    public record ApiStat(Long apiId, String name, String version, long total, long errors, double avgLatencyMs, Instant lastSeen) {}
    public record Bucket(String label, long count) {}
    public record Quota(long used, Integer limit, int windowSeconds, boolean enabled) {}
    public record LogView(Long id, Instant ts, String correlationId, Long apiId, Long keyId, String clientIp, String method, String path, int status,
                          long latencyMs, Long upstreamLatencyMs, long requestBytes, String outcome, String reason) {}
}
