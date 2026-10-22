package io.apishield.backend.apikey;

import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.api.ApiDefinition;
import io.apishield.backend.api.ApiRepository;
import io.apishield.backend.api.ApiStatus;
import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.config.AppProperties;
import io.apishield.backend.security.AuthUser;
import io.apishield.common.key.ApiKeyCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ApiKeyService {
    private static final int MAX_ACTIVE_KEYS_PER_USER = 20;
    private final ApiKeyRepository keys; private final ApiRepository apis; private final RequestLogRepository logs;
    private final AppProperties props; private final AuditService audit;

    public ApiKeyService(ApiKeyRepository keys, ApiRepository apis, RequestLogRepository logs, AppProperties props, AuditService audit) {
        this.keys = keys; this.apis = apis; this.logs = logs; this.props = props; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDtos.KeyView> list(AuthUser u, boolean all) {
        List<ApiKey> ks = (all && u.has("ADMIN")) ? keys.findAllByOrderByCreatedAtDesc() : keys.findByOwnerIdOrderByCreatedAtDesc(u.id());
        Map<Long, Long> usage = new HashMap<>();
        if (!ks.isEmpty()) logs.usageByKey(ks.stream().map(ApiKey::getId).toList(), Instant.now().minus(Duration.ofHours(24)))
                .forEach(r -> usage.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue()));
        return ks.stream().map(k -> view(k, usage.getOrDefault(k.getId(), 0L))).toList();
    }

    @Transactional
    public ApiKeyDtos.KeyWithSecret create(AuthUser u, ApiKeyDtos.CreateRequest r) {
        if (keys.countByOwnerIdAndRevokedFalse(u.id()) >= MAX_ACTIVE_KEYS_PER_USER) {
            audit.denied("API_KEY_CREATE", "API_KEY", null, "active key limit reached");
            throw ApiException.conflict("KEY_LIMIT", "Active key limit reached (" + MAX_ACTIVE_KEYS_PER_USER + "). Revoke an existing key first.");
        }
        List<ApiDefinition> targets = apis.findByIdIn(r.apiIds());
        if (targets.size() != r.apiIds().size() || targets.stream().anyMatch(a -> a.getStatus() != ApiStatus.ACTIVE))
            throw ApiException.badRequest("API_NOT_AVAILABLE", "Keys can only be scoped to existing, active APIs");
        int days = r.expiresInDays() == null ? 90 : r.expiresInDays();
        ApiKeyCodec.Generated g = ApiKeyCodec.generate(props.getApiKeyPepper());
        ApiKey k = new ApiKey();
        k.setName(r.name().trim()); k.setDescription(r.description()); k.setPrefix(g.prefix()); k.setKeyHash(g.hash());
        k.setOwnerId(u.id()); k.setCreatedAt(Instant.now()); k.setExpiresAt(Instant.now().plus(Duration.ofDays(days)));
        k.setQuotaRequests(r.quotaRequests()); k.setApiIds(new HashSet<>(r.apiIds())); k.setScopes(new HashSet<>(r.scopes()));
        k = keys.save(k);
        audit.success("API_KEY_CREATED", "API_KEY", String.valueOf(k.getId()), "prefix=" + k.getPrefix() + " apis=" + k.getApiIds());
        return new ApiKeyDtos.KeyWithSecret(view(k, 0), g.rawKey());
    }

    @Transactional
    public void revoke(AuthUser u, Long id) {
        ApiKey k = mine(u, id);
        if (!k.isRevoked()) { k.setRevoked(true); k.setRevokedAt(Instant.now()); }
        audit.success("API_KEY_REVOKED", "API_KEY", String.valueOf(id), "prefix=" + k.getPrefix());
    }

    /** Rotation issues a new key with identical access and immediately revokes the old one. */
    @Transactional
    public ApiKeyDtos.KeyWithSecret rotate(AuthUser u, Long id) {
        ApiKey old = mine(u, id);
        if (old.isRevoked()) throw ApiException.conflict("KEY_REVOKED", "A revoked key cannot be rotated");
        ApiKeyCodec.Generated g = ApiKeyCodec.generate(props.getApiKeyPepper());
        ApiKey n = new ApiKey();
        n.setName(old.getName()); n.setDescription(old.getDescription()); n.setPrefix(g.prefix()); n.setKeyHash(g.hash());
        n.setOwnerId(old.getOwnerId()); n.setCreatedAt(Instant.now()); n.setQuotaRequests(old.getQuotaRequests());
        n.setExpiresAt(old.getExpiresAt() != null && old.getExpiresAt().isAfter(Instant.now()) ? old.getExpiresAt() : Instant.now().plus(Duration.ofDays(90)));
        n.setApiIds(new HashSet<>(old.getApiIds())); n.setScopes(new HashSet<>(old.getScopes())); n.setRotatedFrom(old.getId());
        n = keys.save(n);
        old.setRevoked(true); old.setRevokedAt(Instant.now());
        audit.success("API_KEY_ROTATED", "API_KEY", String.valueOf(old.getId()), "old=" + old.getPrefix() + " new=" + n.getPrefix());
        return new ApiKeyDtos.KeyWithSecret(view(n, 0), g.rawKey());
    }

    private ApiKey mine(AuthUser u, Long id) {
        ApiKey k = keys.findById(id).orElseThrow(() -> ApiException.notFound("API key"));
        if (!k.getOwnerId().equals(u.id()) && !u.has("ADMIN")) {
            audit.denied("API_KEY_ACCESS", "API_KEY", String.valueOf(id), "not owner");
            throw ApiException.notFound("API key"); // do not reveal existence
        }
        return k;
    }

    static ApiKeyDtos.KeyView view(ApiKey k, long usage) {
        return new ApiKeyDtos.KeyView(k.getId(), k.getName(), k.getDescription(), k.getPrefix(), "ask_" + k.getPrefix() + "_••••••••",
                k.getApiIds(), k.getScopes(), k.getExpiresAt(), k.status(Instant.now()), k.getRevokedAt(), k.getQuotaRequests(), k.getLastUsedAt(), k.getCreatedAt(), usage, k.getOwnerId());
    }
}
