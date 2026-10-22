package io.apishield.backend.threat;

import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.config.AppProperties;
import io.apishield.common.model.Outcomes;
import io.apishield.common.model.TrafficEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Explainable, rule-based detection (no ML). Each rule: outcome(s) -> event type, severity and threshold of occurrences from one client IP
 * inside the configured window. An open duplicate for the same (type, client, api) inside the window is not re-raised.
 */
@Service
public class ThreatDetector {
    private final RequestLogRepository logs; private final SecurityEventRepository events; private final AppProperties props;
    public ThreatDetector(RequestLogRepository logs, SecurityEventRepository events, AppProperties props) { this.logs = logs; this.events = events; this.props = props; }

    record Rule(String type, String severity, List<String> outcomes, int threshold, String label) {}

    private List<Rule> rules(TrafficEvent e) {
        var d = props.getDetection();
        String authType = e.reason() != null && e.reason().contains("API_KEY") ? "REPEATED_INVALID_API_KEY" : "REPEATED_AUTH_FAILURES";
        return switch (e.outcome()) {
            case Outcomes.AUTH_FAILED -> List.of(new Rule(authType, "HIGH", List.of(Outcomes.AUTH_FAILED), d.getAuthFailureThreshold(), "failed authentication attempts"));
            case Outcomes.RATE_LIMITED -> List.of(new Rule("EXCESSIVE_REQUEST_RATE", "MEDIUM", List.of(Outcomes.RATE_LIMITED), d.getRateLimitThreshold(), "rate-limited requests"));
            case Outcomes.FORBIDDEN, Outcomes.METHOD_NOT_ALLOWED -> List.of(new Rule("REPEATED_BLOCKED_REQUESTS", "MEDIUM",
                    List.of(Outcomes.FORBIDDEN, Outcomes.METHOD_NOT_ALLOWED), d.getBlockedRequestThreshold(), "requests denied by access policy"));
            case Outcomes.PAYLOAD_TOO_LARGE -> List.of(new Rule("LARGE_REQUESTS", "LOW", List.of(Outcomes.PAYLOAD_TOO_LARGE), d.getLargeRequestThreshold(), "oversized requests"));
            case Outcomes.DESTINATION_BLOCKED -> List.of(new Rule("DESTINATION_POLICY_VIOLATION", "CRITICAL", List.of(Outcomes.DESTINATION_BLOCKED), 1, "upstream destination policy violations"));
            default -> List.of();
        };
    }

    /** Evaluates one representative event per (client, api, rule type) from a batch. */
    public int evaluate(List<TrafficEvent> batch) {
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofMinutes(props.getDetection().getWindowMinutes()));
        Set<String> seen = new HashSet<>();
        int created = 0;
        for (TrafficEvent e : batch) {
            if (e.clientIp() == null) continue;
            for (Rule r : rules(e)) {
                if (!seen.add(r.type() + "|" + e.clientIp() + "|" + e.apiId())) continue;
                long count = logs.countByClientIpAndOutcomeInAndTsAfter(e.clientIp(), r.outcomes(), since);
                if (count < r.threshold()) continue;
                if (events.existsByTypeAndClientIpAndApiIdAndCreatedAtAfter(r.type(), e.clientIp(), e.apiId(), since)) continue;
                SecurityEvent s = new SecurityEvent();
                s.setCreatedAt(now); s.setType(r.type()); s.setSeverity(r.severity()); s.setApiId(e.apiId()); s.setClientIp(e.clientIp());
                s.setCorrelationId(e.correlationId()); s.setOccurrences((int) Math.min(count, Integer.MAX_VALUE));
                s.setDescription(count + " " + r.label() + " from " + e.clientIp() + " within " + props.getDetection().getWindowMinutes() + " minutes");
                events.save(s); created++;
            }
        }
        return created;
    }
}
