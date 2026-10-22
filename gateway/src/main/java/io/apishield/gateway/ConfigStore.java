package io.apishield.gateway;

import io.apishield.common.model.KeyConfig;
import io.apishield.common.model.RouteConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Immutable, atomically swapped configuration snapshot. Requests never block on the management API. */
@Component
public class ConfigStore {
    public record Snapshot(String version, Instant loadedAt, List<RouteConfig> routes, Map<String, KeyConfig> keysByPrefix) {}

    private final AtomicReference<Snapshot> ref = new AtomicReference<>();
    private volatile Instant lastSuccess;

    public void replace(String version, List<RouteConfig> routes, List<KeyConfig> keys) {
        ref.set(new Snapshot(version, Instant.now(), List.copyOf(routes), keys.stream().collect(Collectors.toUnmodifiableMap(KeyConfig::prefix, k -> k, (a, b) -> a))));
        lastSuccess = Instant.now();
    }
    public void markFresh() { lastSuccess = Instant.now(); }
    public Snapshot current() { return ref.get(); }
    public boolean loaded() { return ref.get() != null; }
    public Instant lastSuccess() { return lastSuccess; }
    public long staleSeconds() { return lastSuccess == null ? Long.MAX_VALUE : Instant.now().getEpochSecond() - lastSuccess.getEpochSecond(); }
}
