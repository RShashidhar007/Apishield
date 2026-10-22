package io.apishield.gateway;

import io.apishield.common.model.GatewayConfig;
import io.apishield.common.model.RouteConfig;
import io.apishield.common.net.DestinationPolicy;
import io.apishield.common.net.DestinationRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pull-based configuration sync. Consistency model: eventually consistent, bounded by gateway.config-refresh-ms (default 5s).
 * If the management API is unreachable the last good snapshot keeps being served (availability over freshness) unless
 * gateway.max-stale-seconds > 0, in which case protected requests fail closed once the snapshot is older than that.
 */
@Service
public class ConfigSyncService {
    private static final Logger log = LoggerFactory.getLogger(ConfigSyncService.class);
    private final ConfigStore store; private final GatewayProperties props; private final DestinationPolicy policy;
    private final ApplicationEventPublisher publisher; private final WebClient client;

    public ConfigSyncService(ConfigStore store, GatewayProperties props, DestinationPolicy policy, ApplicationEventPublisher publisher, WebClient.Builder builder) {
        this.store = store; this.props = props; this.policy = policy; this.publisher = publisher;
        this.client = builder.baseUrl(props.getManagementUrl()).defaultHeader("X-Gateway-Token", props.getGatewayToken()).build();
    }

    @Scheduled(fixedDelayString = "${gateway.config-refresh-ms:5000}", initialDelay = 0)
    public void sync() {
        try {
            GatewayConfig cfg = client.get().uri("/internal/v1/gateway/config").retrieve().bodyToMono(GatewayConfig.class).timeout(Duration.ofSeconds(5)).block();
            if (cfg == null) throw new IllegalStateException("empty configuration");
            var cur = store.current();
            if (cur != null && Objects.equals(cur.version(), cfg.version())) { store.markFresh(); return; }
            List<RouteConfig> valid = new ArrayList<>();
            for (RouteConfig r : cfg.routes()) {
                try {
                    var uri = policy.validateUrl(r.upstreamUrl());
                    // IP-literal targets may bypass DNS resolution (and therefore the connect-time resolver), so validate them here.
                    String h = uri.getHost();
                    if (h.matches("[0-9.]+") || h.contains(":")) policy.validateAddress(h, java.net.InetAddress.getByName(h.replaceAll("^\\[|\\]$", "")));
                    valid.add(r);
                }
                catch (DestinationRejectedException | java.net.UnknownHostException e) { log.error("Route {} ({}) skipped: destination policy violation", r.id(), r.gatewayPath()); }
            }
            store.replace(cfg.version(), valid, cfg.keys());
            publisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Configuration {} loaded: {} routes, {} keys", cfg.version(), valid.size(), cfg.keys().size());
        } catch (Exception e) {
            log.warn("Configuration refresh failed ({}). Serving last known configuration (stale {}s)", e.getClass().getSimpleName(), store.loaded() ? store.staleSeconds() : -1);
        }
    }
}
