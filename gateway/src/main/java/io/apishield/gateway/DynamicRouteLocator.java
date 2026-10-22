package io.apishield.gateway;

import io.apishield.common.model.RouteConfig;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds one Spring Cloud Gateway Route per ACTIVE API from the current snapshot. Longest public prefix wins.
 * The route's URI is only the upstream origin; the request path is rewritten by PolicyFilter. Timeouts are per route via metadata.
 * Routes are rebuilt when ConfigSyncService publishes a RefreshRoutesEvent.
 */
@Component
public class DynamicRouteLocator implements RouteLocator {
    private final ConfigStore store; private final Authenticator auth; private final RateLimiter limiter; private final GatewayProperties props; private final ClientIpResolver ips;

    public DynamicRouteLocator(ConfigStore store, Authenticator auth, RateLimiter limiter, GatewayProperties props, ClientIpResolver ips) {
        this.store = store; this.auth = auth; this.limiter = limiter; this.props = props; this.ips = ips;
    }

    @Override
    public Flux<Route> getRoutes() {
        var snap = store.current();
        if (snap == null) return Flux.empty();
        List<Route> out = new ArrayList<>();
        snap.routes().stream().sorted(Comparator.comparingInt((RouteConfig r) -> r.gatewayPath().length()).reversed()).forEach(r -> {
            URI u = URI.create(r.upstreamUrl());
            URI origin = URI.create(u.getScheme() + "://" + u.getHost() + (u.getPort() == -1 ? "" : ":" + u.getPort()));
            String prefix = "/gateway" + r.gatewayPath();
            out.add(Route.async().id("api-" + r.id()).uri(origin).order(-r.gatewayPath().length())
                    .predicate(ex -> { String p = ex.getRequest().getURI().getRawPath(); return p.equals(prefix) || p.startsWith(prefix + "/"); })
                    .filter(new OrderedGatewayFilter(new PolicyFilter(r, auth, limiter, store, props, ips), 0))
                    .metadata(Map.<String, Object>of(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, r.responseTimeoutMs(), RouteMetadataUtils.CONNECT_TIMEOUT_ATTR, r.connectTimeoutMs()))
                    .build());
        });
        return Flux.fromIterable(out);
    }
}
