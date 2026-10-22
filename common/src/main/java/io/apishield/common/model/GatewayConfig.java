package io.apishield.common.model;

import java.time.Instant;
import java.util.List;

public record GatewayConfig(String version, Instant generatedAt, List<RouteConfig> routes, List<KeyConfig> keys) {}
