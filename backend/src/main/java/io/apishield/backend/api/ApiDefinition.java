package io.apishield.backend.api;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter @Setter
@Entity @Table(name = "apis")
public class ApiDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false, length = 32) private String version;
    @Column(nullable = false, length = 16) private String environment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ApiStatus status = ApiStatus.INACTIVE;
    @Column(name = "gateway_path", nullable = false, length = 120) private String gatewayPath;
    @Column(name = "upstream_url", nullable = false, length = 500) private String upstreamUrl;
    @Column(nullable = false, length = 80) private String methods;
    @Column(name = "auth_policy", nullable = false, length = 16) private String authPolicy;
    @Column(name = "rate_limit_enabled", nullable = false) private boolean rateLimitEnabled;
    @Column(name = "rate_limit_requests", nullable = false) private int rateLimitRequests;
    @Column(name = "rate_limit_window_seconds", nullable = false) private int rateLimitWindowSeconds;
    @Column(name = "rate_limit_burst", nullable = false) private int rateLimitBurst;
    @Column(name = "connect_timeout_ms", nullable = false) private int connectTimeoutMs;
    @Column(name = "response_timeout_ms", nullable = false) private int responseTimeoutMs;
    @Column(name = "max_request_bytes", nullable = false) private long maxRequestBytes;
    @Column(name = "allowed_headers", length = 500) private String allowedHeaders;
    @Column(name = "cors_origins", length = 500) private String corsOrigins;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Set<String> methodSet() { return csv(methods).stream().collect(Collectors.toCollection(java.util.TreeSet::new)); }
    public List<String> headerList() { return csv(allowedHeaders); }
    public List<String> originList() { return csv(corsOrigins); }
    static List<String> csv(String s) { return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList(); }
}
