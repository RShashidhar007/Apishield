package io.apishield.backend.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter @Setter
@Entity @Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Instant ts;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "actor_email", length = 254) private String actorEmail;
    @Column(nullable = false, length = 64) private String action;
    @Column(name = "resource_type", nullable = false, length = 32) private String resourceType;
    @Column(name = "resource_id", length = 64) private String resourceId;
    @Column(nullable = false, length = 16) private String outcome;
    @Column(length = 500) private String detail;
    @Column(name = "correlation_id", length = 64) private String correlationId;
}
