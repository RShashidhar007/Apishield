package io.apishield.backend.audit;

import io.apishield.backend.common.CorrelationIdFilter;
import io.apishield.backend.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Durable audit trail. Written in its own transaction so failures/denials are kept even if the caller rolls back. Never pass secrets in detail. */
@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String action, String type, String id, String detail) { record(action, type, id, "SUCCESS", detail, null, null); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String action, String type, String id, String detail) { record(action, type, id, "FAILURE", detail, null, null); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void denied(String action, String type, String id, String detail) { record(action, type, id, "DENIED", detail, null, null); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void asActor(Long actorId, String email, String action, String type, String id, String outcome, String detail) { record(action, type, id, outcome, detail, actorId, email); }

    private void record(String action, String type, String id, String outcome, String detail, Long actorId, String actorEmail) {
        AuditLog l = new AuditLog();
        l.setTs(Instant.now()); l.setAction(action); l.setResourceType(type); l.setResourceId(id); l.setOutcome(outcome);
        l.setDetail(detail == null ? null : detail.substring(0, Math.min(detail.length(), 500)));
        l.setCorrelationId(CorrelationIdFilter.current());
        if (actorId == null) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.getPrincipal() instanceof AuthUser u) { actorId = u.id(); actorEmail = u.email(); }
            else if (a != null && "gateway".equals(a.getPrincipal())) actorEmail = "gateway";
        }
        l.setActorId(actorId); l.setActorEmail(actorEmail);
        repo.save(l);
    }
}
