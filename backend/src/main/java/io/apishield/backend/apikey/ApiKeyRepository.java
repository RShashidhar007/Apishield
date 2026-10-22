package io.apishield.backend.apikey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<ApiKey> findAllByOrderByCreatedAtDesc();
    long countByOwnerIdAndRevokedFalse(Long ownerId);
    @Modifying @Query("update ApiKey k set k.lastUsedAt = :ts where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :ts)")
    int touch(Long id, Instant ts);
}
