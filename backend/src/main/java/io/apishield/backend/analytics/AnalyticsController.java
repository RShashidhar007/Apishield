package io.apishield.backend.analytics;

import io.apishield.backend.common.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
public class AnalyticsController {
    private final AnalyticsService svc;
    public AnalyticsController(AnalyticsService svc) { this.svc = svc; }
    private static final String ISO = "yyyy-MM-dd'T'HH:mm:ss.SSSX";

    @GetMapping("/summary") AnalyticsDtos.Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(required = false) Long apiId) { return svc.summary(from, to, apiId); }

    @GetMapping("/timeseries") List<AnalyticsDtos.TimePoint> timeseries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(required = false) Long apiId) { return svc.timeseries(from, to, apiId); }

    @GetMapping("/by-api") List<AnalyticsDtos.ApiStat> byApi(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) { return svc.byApi(from, to); }

    @GetMapping("/status-codes") List<AnalyticsDtos.Bucket> status(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(required = false) Long apiId) { return svc.statusDistribution(from, to, apiId); }

    @GetMapping("/methods") List<AnalyticsDtos.Bucket> methods(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to, @RequestParam(required = false) Long apiId) { return svc.methodDistribution(from, to, apiId); }

    @GetMapping("/quota") AnalyticsDtos.Quota quota(@RequestParam Long apiId, @RequestParam(required = false) Long keyId) { return svc.quota(apiId, keyId); }

    @GetMapping("/logs") PageResponse<AnalyticsDtos.LogView> logs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                                  @RequestParam(required = false) Long apiId, @RequestParam(required = false) String outcome, @RequestParam(required = false) Integer status,
                                                                  @RequestParam(required = false) String correlationId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return svc.logs(from, to, apiId, outcome, status, correlationId, page, size);
    }
}
