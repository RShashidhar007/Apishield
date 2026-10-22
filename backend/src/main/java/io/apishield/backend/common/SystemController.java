package io.apishield.backend.common;

import io.apishield.backend.config.AppProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only settings for the admin Settings screen. Secrets are never returned. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final AppProperties p;
    public SystemController(AppProperties p) { this.p = p; }

    public record Info(String version, String jwtIssuer, long accessTokenSeconds, long refreshTokenSeconds, List<String> allowedUpstreamHosts,
                       boolean allowPrivateUpstreams, int maxFailedLogins, int lockMinutes, AppProperties.Detection detection, AppProperties.Retention retention) {}

    @GetMapping("/info") @PreAuthorize("hasAnyRole('ADMIN','API_MANAGER')")
    Info info() {
        return new Info("1.0.0", p.getJwt().getIssuer(), p.getJwt().getAccessTtl().toSeconds(), p.getJwt().getRefreshTtl().toSeconds(),
                p.getDestination().getAllowedHosts(), p.getDestination().isAllowPrivateForAllowedHosts(), p.getLogin().getMaxFailedAttempts(), p.getLogin().getLockMinutes(),
                p.getDetection(), p.getRetention());
    }
}
