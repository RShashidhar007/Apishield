package io.apishield.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying @Query("update RefreshToken t set t.revoked = true where t.familyId = :family") int revokeFamily(String family);
    @Modifying @Query("update RefreshToken t set t.revoked = true where t.userId = :userId") int revokeAllForUser(Long userId);
    @Modifying @Query("delete from RefreshToken t where t.expiresAt < :cutoff") int deleteExpired(Instant cutoff);
}
