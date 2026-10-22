package io.apishield.backend.threat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long>, JpaSpecificationExecutor<SecurityEvent> {
    boolean existsByTypeAndClientIpAndApiIdAndCreatedAtAfter(String type, String clientIp, Long apiId, Instant after);
    long countByAcknowledgedFalse();
    @Modifying @Query("delete from SecurityEvent e where e.createdAt < :cutoff") int purge(Instant cutoff);
}
