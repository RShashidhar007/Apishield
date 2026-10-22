package io.apishield.backend.audit;

import io.apishield.backend.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditLogRepository repo;
    public AuditController(AuditLogRepository repo) { this.repo = repo; }

    public record AuditView(Long id, Instant ts, Long actorId, String actorEmail, String action, String resourceType, String resourceId, String outcome, String detail, String correlationId) {}

    @GetMapping
    PageResponse<AuditView> search(@RequestParam(required = false) String action, @RequestParam(required = false) String actor,
                                   @RequestParam(required = false) String outcome,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        var spec = (org.springframework.data.jpa.domain.Specification<AuditLog>) (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (action != null && !action.isBlank()) p.add(cb.like(r.get("action"), action.toUpperCase().replace("%", "") + "%"));
            if (actor != null && !actor.isBlank()) p.add(cb.like(cb.lower(r.get("actorEmail")), "%" + actor.toLowerCase().replace("%", "") + "%"));
            if (outcome != null && !outcome.isBlank()) p.add(cb.equal(r.get("outcome"), outcome));
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.get("ts"), from));
            if (to != null) p.add(cb.lessThan(r.get("ts"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("ts").descending())),
                a -> new AuditView(a.getId(), a.getTs(), a.getActorId(), a.getActorEmail(), a.getAction(), a.getResourceType(), a.getResourceId(), a.getOutcome(), a.getDetail(), a.getCorrelationId()));
    }
}
