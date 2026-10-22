package io.apishield.gateway;

/** Per-request mutable context shared by the web filter, policy filter and error handler. */
public class RequestCtx {
    public static final String ATTR = "apishield.ctx";
    public final String correlationId; public final long startNanos = System.nanoTime(); public final java.time.Instant startedAt = java.time.Instant.now();
    public volatile Long apiId, keyId, userId; public volatile String clientIp = "unknown", outcome, reason; public volatile long requestBytes; public volatile long upstreamStartNanos;
    public RequestCtx(String correlationId) { this.correlationId = correlationId; }
}
