package io.apishield.backend.gateway;

import io.apishield.backend.analytics.RequestLog;
import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.apikey.ApiKeyRepository;
import io.apishield.backend.threat.ThreatDetector;
import io.apishield.common.model.IngestBatch;
import io.apishield.common.model.TrafficEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Accepts batches of traffic events from gateways, persists them, updates key last-used and runs threat rules. */
@Service
public class IngestService {
    static final int MAX_BATCH = 1000;
    private final RequestLogRepository logs; private final ApiKeyRepository keys; private final ThreatDetector detector;
    public IngestService(RequestLogRepository logs, ApiKeyRepository keys, ThreatDetector detector) { this.logs = logs; this.keys = keys; this.detector = detector; }

    @Transactional
    public int ingest(IngestBatch batch) {
        if (batch == null || batch.events() == null || batch.events().isEmpty()) return 0;
        List<TrafficEvent> events = batch.events().size() > MAX_BATCH ? batch.events().subList(0, MAX_BATCH) : batch.events();
        Instant now = Instant.now();
        Map<Long, Instant> lastUsed = new HashMap<>();
        List<RequestLog> rows = events.stream().map(e -> {
            RequestLog r = new RequestLog();
            r.setTs(e.timestamp() == null || e.timestamp().isAfter(now.plusSeconds(60)) ? now : e.timestamp());
            r.setCorrelationId(trunc(e.correlationId(), 64)); r.setApiId(e.apiId()); r.setKeyId(e.keyId()); r.setUserId(e.userId());
            r.setClientIp(trunc(e.clientIp(), 64)); r.setMethod(trunc(e.method(), 10)); r.setPath(trunc(e.path(), 512));
            r.setStatus(e.status()); r.setLatencyMs(Math.max(0, e.latencyMs())); r.setUpstreamLatencyMs(e.upstreamLatencyMs());
            r.setRequestBytes(Math.max(0, e.requestBytes())); r.setOutcome(trunc(e.outcome(), 32)); r.setReason(trunc(e.reason(), 64));
            if (e.keyId() != null && "OK".equals(e.outcome())) lastUsed.merge(e.keyId(), r.getTs(), (a, b) -> a.isAfter(b) ? a : b);
            return r;
        }).toList();
        logs.saveAll(rows);
        lastUsed.forEach(keys::touch);
        detector.evaluate(events);
        return rows.size();
    }

    private static String trunc(String s, int n) { return s == null ? "" : s.substring(0, Math.min(s.length(), n)); }
}
