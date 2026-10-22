package io.apishield.backend.analytics;

import io.apishield.backend.api.ApiDefinition;
import io.apishield.backend.api.ApiRepository;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final RequestLogRepository repo; private final ApiRepository apis;
    public AnalyticsService(RequestLogRepository repo, ApiRepository apis) { this.repo = repo; this.apis = apis; }

    private static Instant[] range(Instant from, Instant to) {
        Instant t = to == null ? Instant.now().plusSeconds(1) : to;
        Instant f = from == null ? t.minus(Duration.ofHours(24)) : from;
        if (!f.isBefore(t)) throw ApiException.badRequest("INVALID_RANGE", "'from' must be before 'to'");
        if (Duration.between(f, t).toDays() > 90) throw ApiException.badRequest("RANGE_TOO_LARGE", "Maximum range is 90 days");
        return new Instant[]{f, t};
    }
    private static long id(Long apiId) { return apiId == null ? 0 : apiId; }
    private static long l(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
    private static double d(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }
    static Instant ts(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC);
        if (o instanceof OffsetDateTime t) return t.toInstant();
        throw new IllegalStateException("Unsupported timestamp type " + o.getClass());
    }

    public AnalyticsDtos.Summary summary(Instant from, Instant to, Long apiId) {
        Instant[] r = range(from, to);
        Object[] s = repo.summary(r[0], r[1], id(apiId)).get(0);
        long total = l(s[0]), errors = l(s[2]);
        List<Object> p = repo.p95(r[0], r[1], id(apiId));
        return new AnalyticsDtos.Summary(r[0], r[1], total, l(s[1]), errors, total == 0 ? 0 : (double) errors / total, d(s[3]),
                p.isEmpty() ? 0 : d(p.get(0)), d(s[8]), l(s[4]), l(s[5]), l(s[6]), l(s[7]));
    }

    public List<AnalyticsDtos.TimePoint> timeseries(Instant from, Instant to, Long apiId) {
        Instant[] r = range(from, to);
        long minutes = Math.max(1, Duration.between(r[0], r[1]).toMinutes());
        int bucket = minutes <= 120 ? 1 : minutes <= 720 ? 5 : minutes <= 2880 ? 15 : minutes <= 10080 ? 60 : 360;
        return repo.timeseries(r[0], r[1], id(apiId), bucket).stream()
                .map(x -> new AnalyticsDtos.TimePoint(ts(x[0]), l(x[1]), l(x[2]), d(x[3]), l(x[4]))).toList();
    }

    public List<AnalyticsDtos.ApiStat> byApi(Instant from, Instant to) {
        Instant[] r = range(from, to);
        return repo.byApi(r[0], r[1]).stream().map(x -> new AnalyticsDtos.ApiStat(x[0] == null ? null : l(x[0]), (String) x[1], (String) x[2], l(x[3]), l(x[4]), d(x[5]), ts(x[6]))).toList();
    }

    public List<AnalyticsDtos.Bucket> statusDistribution(Instant from, Instant to, Long apiId) {
        Instant[] r = range(from, to);
        return repo.statusDistribution(r[0], r[1], id(apiId)).stream().map(x -> new AnalyticsDtos.Bucket(String.valueOf(l(x[0])), l(x[1]))).toList();
    }

    public List<AnalyticsDtos.Bucket> methodDistribution(Instant from, Instant to, Long apiId) {
        Instant[] r = range(from, to);
        return repo.methodDistribution(r[0], r[1], id(apiId)).stream().map(x -> new AnalyticsDtos.Bucket((String) x[0], l(x[1]))).toList();
    }

    /** Approximate quota usage from persisted traffic (ingest lag applies); the authoritative counter lives in Redis in the gateway. */
    public AnalyticsDtos.Quota quota(Long apiId, Long keyId) {
        ApiDefinition a = apis.findById(apiId).orElseThrow(() -> ApiException.notFound("API"));
        Instant since = Instant.now().minusSeconds(a.getRateLimitWindowSeconds());
        return new AnalyticsDtos.Quota(repo.countSince(apiId, keyId == null ? 0 : keyId, since), a.isRateLimitEnabled() ? a.getRateLimitRequests() : null, a.getRateLimitWindowSeconds(), a.isRateLimitEnabled());
    }

    public PageResponse<AnalyticsDtos.LogView> logs(Instant from, Instant to, Long apiId, String outcome, Integer status, String correlationId, int page, int size) {
        Instant[] r = range(from, to);
        Specification<RequestLog> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(root.get("ts"), r[0])); p.add(cb.lessThan(root.get("ts"), r[1]));
            if (apiId != null) p.add(cb.equal(root.get("apiId"), apiId));
            if (outcome != null && !outcome.isBlank()) p.add(cb.equal(root.get("outcome"), outcome));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (correlationId != null && !correlationId.isBlank()) p.add(cb.equal(root.get("correlationId"), correlationId));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageRequest.of(page, Math.min(size, 100), Sort.by("ts").descending())),
                x -> new AnalyticsDtos.LogView(x.getId(), x.getTs(), x.getCorrelationId(), x.getApiId(), x.getKeyId(), x.getClientIp(), x.getMethod(), x.getPath(), x.getStatus(),
                        x.getLatencyMs(), x.getUpstreamLatencyMs(), x.getRequestBytes(), x.getOutcome(), x.getReason()));
    }
}
