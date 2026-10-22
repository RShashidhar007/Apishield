package io.apishield.backend.threat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity @Table(name = "security_events")
public class SecurityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(nullable = false, length = 48) private String type;
    @Column(nullable = false, length = 16) private String severity;
    @Column(name = "api_id") private Long apiId;
    @Column(name = "client_ip", length = 64) private String clientIp;
    @Column(name = "correlation_id", length = 64) private String correlationId;
    @Column(nullable = false, length = 500) private String description;
    @Column(nullable = false) private int occurrences;
    @Column(nullable = false) private boolean acknowledged;
    @Column(name = "acknowledged_by") private Long acknowledgedBy;
    @Column(name = "acknowledged_at") private Instant acknowledgedAt;
    @Column(length = 1000) private String note;
}
