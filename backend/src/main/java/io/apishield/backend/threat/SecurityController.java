package io.apishield.backend.threat;

import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.common.PageResponse;
import io.apishield.backend.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/security")
@PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
public class SecurityController {
    private final SecurityEventRepository repo; private final AuditService audit;
    public SecurityController(SecurityEventRepository repo, AuditService audit) { this.repo = repo; this.audit = audit; }

    public record EventView(Long id, Instant createdAt, String type, String severity, Long apiId, String clientIp, String correlationId, String description,
                            int occurrences, boolean acknowledged, Long acknowledgedBy, Instant acknowledgedAt, String note) {
        static EventView of(SecurityEvent e) { return new EventView(e.getId(), e.getCreatedAt(), e.getType(), e.getSeverity(), e.getApiId(), e.getClientIp(), e.getCorrelationId(),
                e.getDescription(), e.getOccurrences(), e.isAcknowledged(), e.getAcknowledgedBy(), e.getAcknowledgedAt(), e.getNote()); }
    }
    public record AckRequest(@Size(max = 1000) String note) {}
    public record Counts(long unacknowledged) {}

    @GetMapping("/events")
    PageResponse<EventView> list(@RequestParam(required = false) String severity, @RequestParam(required = false) Boolean acknowledged,
                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var spec = (org.springframework.data.jpa.domain.Specification<SecurityEvent>) (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (severity != null && !severity.isBlank()) p.add(cb.equal(r.get("severity"), severity));
            if (acknowledged != null) p.add(cb.equal(r.get("acknowledged"), acknowledged));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())), EventView::of);
    }

    @GetMapping("/events/{id}") EventView get(@PathVariable Long id) { return EventView.of(repo.findById(id).orElseThrow(() -> ApiException.notFound("Security event"))); }

    @GetMapping("/counts") Counts counts() { return new Counts(repo.countByAcknowledgedFalse()); }

    @PostMapping("/events/{id}/acknowledge") @Transactional
    EventView acknowledge(@PathVariable Long id, @Valid @RequestBody AckRequest r, @AuthenticationPrincipal AuthUser me) {
        SecurityEvent e = repo.findById(id).orElseThrow(() -> ApiException.notFound("Security event"));
        e.setAcknowledged(true); e.setAcknowledgedBy(me.id()); e.setAcknowledgedAt(Instant.now()); e.setNote(r.note());
        audit.success("SECURITY_EVENT_ACKNOWLEDGED", "SECURITY_EVENT", String.valueOf(id), e.getType());
        return EventView.of(e);
    }
}
