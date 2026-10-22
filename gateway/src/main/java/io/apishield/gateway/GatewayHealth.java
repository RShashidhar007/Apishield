package io.apishield.gateway;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayHealth {
    /** Readiness is DOWN until the first configuration snapshot has been loaded. */
    @Bean HealthIndicator gatewayConfigHealthIndicator(ConfigStore store, TrafficShipper shipper) {
        return () -> {
            if (!store.loaded()) return Health.down().withDetail("reason", "configuration not loaded yet").build();
            return Health.up().withDetail("configVersion", store.current().version()).withDetail("routes", store.current().routes().size())
                    .withDetail("staleSeconds", store.staleSeconds()).withDetail("queuedEvents", shipper.queued()).withDetail("droppedEvents", shipper.dropped()).build();
        };
    }
}
