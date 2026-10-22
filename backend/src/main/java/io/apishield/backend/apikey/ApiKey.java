package io.apishield.backend.apikey;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@Entity @Table(name = "api_keys")
public class ApiKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 500) private String description;
    @Column(nullable = false, unique = true, length = 12) private String prefix;
    @Column(name = "key_hash", nullable = false, length = 64) private String keyHash;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "quota_requests") private Integer quotaRequests;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(name = "rotated_from") private Long rotatedFrom;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_apis", joinColumns = @JoinColumn(name = "key_id"))
    @Column(name = "api_id") private Set<Long> apiIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scopes", joinColumns = @JoinColumn(name = "key_id"))
    @Column(name = "scope", length = 16) private Set<String> scopes = new HashSet<>();

    public String status(Instant now) { return revoked ? "REVOKED" : (expiresAt != null && expiresAt.isBefore(now) ? "EXPIRED" : "ACTIVE"); }
}
