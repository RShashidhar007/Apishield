package io.apishield.backend.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apishield.backend.api.ApiDefinition;
import io.apishield.backend.api.ApiRepository;
import io.apishield.backend.api.ApiStatus;
import io.apishield.backend.apikey.ApiKeyRepository;
import io.apishield.common.model.GatewayConfig;
import io.apishield.common.model.KeyConfig;
import io.apishield.common.model.RouteConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/** Builds the immutable configuration snapshot that gateways poll. Contains key HASHES only. */
@Service
public class GatewayConfigService {
    private final ApiRepository apis; private final ApiKeyRepository keys; private final ObjectMapper mapper;
    public GatewayConfigService(ApiRepository apis, ApiKeyRepository keys, ObjectMapper mapper) { this.apis = apis; this.keys = keys; this.mapper = mapper; }

    @Transactional(readOnly = true)
    public GatewayConfig build() {
        List<RouteConfig> routes = apis.findByStatus(ApiStatus.ACTIVE).stream().map(GatewayConfigService::route).toList();
        List<KeyConfig> ks = keys.findAll().stream()
                .filter(k -> !(k.isRevoked() && k.getRevokedAt() != null && k.getRevokedAt().isBefore(Instant.now().minusSeconds(30L * 86400))))
                .map(k -> new KeyConfig(k.getId(), k.getPrefix(), k.getKeyHash(), k.getOwnerId(), k.getApiIds(), k.getScopes(), k.getExpiresAt(), k.isRevoked(), k.getQuotaRequests())).toList();
        try {
            String json = mapper.writeValueAsString(List.of(routes, ks));
            String version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
            return new GatewayConfig(version, Instant.now(), routes, ks);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static RouteConfig route(ApiDefinition a) {
        return new RouteConfig(a.getId(), a.getName(), a.getVersion(), a.getEnvironment(), a.getGatewayPath(), a.getUpstreamUrl(), a.methodSet(), a.getAuthPolicy(),
                a.isRateLimitEnabled(), a.getRateLimitRequests(), a.getRateLimitWindowSeconds(), a.getRateLimitBurst(), a.getConnectTimeoutMs(), a.getResponseTimeoutMs(),
                a.getMaxRequestBytes(), a.headerList(), a.originList());
    }
}
