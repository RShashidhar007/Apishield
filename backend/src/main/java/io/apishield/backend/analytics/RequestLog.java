package io.apishield.backend.analytics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity @Table(name = "request_logs")
public class RequestLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant ts;
    @Column(name = "correlation_id", nullable = false, length = 64) private String correlationId;
    @Column(name = "api_id") private Long apiId;
    @Column(name = "key_id") private Long keyId;
    @Column(name = "user_id") private Long userId;
    @Column(name = "client_ip", length = 64) private String clientIp;
    @Column(nullable = false, length = 10) private String method;
    @Column(nullable = false, length = 512) private String path;
    @Column(nullable = false) private int status;
    @Column(name = "latency_ms", nullable = false) private long latencyMs;
    @Column(name = "upstream_latency_ms") private Long upstreamLatencyMs;
    @Column(name = "request_bytes", nullable = false) private long requestBytes;
    @Column(nullable = false, length = 32) private String outcome;
    @Column(length = 64) private String reason;
}
