package io.apishield.backend.api;

import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.common.PageResponse;
import io.apishield.backend.security.AuthUser;
import io.apishield.common.net.DestinationPolicy;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApiService {
    private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "transfer-encoding", "connection", "forwarded",
            "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port", "x-real-ip", "x-api-key", "upgrade", "te", "trailer");
    private static final Set<String> RESERVED_PATHS = Set.of("/actuator", "/internal", "/api");

    private final ApiRepository repo; private final DestinationPolicy policy; private final AuditService audit;
    public ApiService(ApiRepository repo, DestinationPolicy policy, AuditService audit) { this.repo = repo; this.policy = policy; this.audit = audit; }

    @Transactional(readOnly = true)
    public PageResponse<ApiDtos.ApiView> search(AuthUser u, String q, String status, String env, int page, int size) {
        boolean manager = u.isAdminOrManager();
        Specification<ApiDefinition> spec = (r, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!manager) p.add(cb.equal(r.get("status"), ApiStatus.ACTIVE));
            else if (status != null && !status.isBlank()) p.add(cb.equal(r.get("status"), ApiStatus.valueOf(status)));
            else p.add(cb.notEqual(r.get("status"), ApiStatus.ARCHIVED));
            if (env != null && !env.isBlank()) p.add(cb.equal(r.get("environment"), env));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase().replace("%", "") + "%";
                p.add(cb.or(cb.like(cb.lower(r.get("name")), like), cb.like(cb.lower(r.get("gatewayPath")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name").ascending().and(Sort.by("version").descending()));
        return PageResponse.of(repo.findAll(spec, pageable), a -> ApiDtos.ApiView.of(a, manager));
    }

    @Transactional(readOnly = true)
    public ApiDtos.ApiView get(AuthUser u, Long id) {
        ApiDefinition a = repo.findById(id).orElseThrow(() -> ApiException.notFound("API"));
        if (!u.isAdminOrManager() && a.getStatus() != ApiStatus.ACTIVE) throw ApiException.notFound("API");
        return ApiDtos.ApiView.of(a, u.isAdminOrManager());
    }

    @Transactional
    public ApiDtos.ApiView create(AuthUser u, ApiDtos.ApiRequest r) {
        ApiDefinition a = new ApiDefinition();
        a.setOwnerId(u.id()); a.setCreatedAt(Instant.now()); a.setStatus(ApiStatus.INACTIVE);
        apply(a, r, null);
        a = repo.save(a);
        audit.success("API_CREATED", "API", String.valueOf(a.getId()), a.getName() + " " + a.getVersion() + " path=" + a.getGatewayPath());
        return ApiDtos.ApiView.of(a, true);
    }

    @Transactional
    public ApiDtos.ApiView update(AuthUser u, Long id, ApiDtos.ApiRequest r) {
        ApiDefinition a = owned(u, id);
        if (a.getStatus() == ApiStatus.ARCHIVED) throw ApiException.conflict("API_ARCHIVED", "Archived APIs cannot be modified");
        apply(a, r, id);
        audit.success("API_UPDATED", "API", String.valueOf(id), "path=" + a.getGatewayPath() + " auth=" + a.getAuthPolicy()
                + " rl=" + a.isRateLimitEnabled() + ":" + a.getRateLimitRequests() + "/" + a.getRateLimitWindowSeconds() + "s");
        return ApiDtos.ApiView.of(a, true);
    }

    @Transactional
    public ApiDtos.ApiView setStatus(AuthUser u, Long id, String status) {
        ApiDefinition a = owned(u, id);
        if (a.getStatus() == ApiStatus.ARCHIVED) throw ApiException.conflict("API_ARCHIVED", "Archived APIs cannot change status");
        a.setStatus(ApiStatus.valueOf(status)); a.setUpdatedAt(Instant.now());
        audit.success(status.equals("ACTIVE") ? "API_ACTIVATED" : "API_DEACTIVATED", "API", String.valueOf(id), null);
        return ApiDtos.ApiView.of(a, true);
    }

    /** Safe archival: keeps history (logs, keys) and frees the gateway path. */
    @Transactional
    public void archive(AuthUser u, Long id) {
        ApiDefinition a = owned(u, id);
        a.setStatus(ApiStatus.ARCHIVED); a.setUpdatedAt(Instant.now());
        audit.success("API_ARCHIVED", "API", String.valueOf(id), null);
    }

    private ApiDefinition owned(AuthUser u, Long id) {
        ApiDefinition a = repo.findById(id).orElseThrow(() -> ApiException.notFound("API"));
        if (!u.has("ADMIN") && !a.getOwnerId().equals(u.id())) {
            audit.denied("API_MODIFY", "API", String.valueOf(id), "not owner");
            throw ApiException.forbidden("Only the owner or an administrator can change this API");
        }
        return a;
    }

    private void apply(ApiDefinition a, ApiDtos.ApiRequest r, Long selfId) {
        // 1. destination (SSRF) checks: allow-list, scheme, port, and resolved-address policy
        URI uri = policy.validateUrl(r.upstreamUrl());
        policy.validateResolved(uri.getHost(), InetAddress::getAllByName);
        // 2. route checks
        String path = r.gatewayPath();
        for (String reserved : RESERVED_PATHS) if (path.equals(reserved) || path.startsWith(reserved + "/"))
            throw ApiException.badRequest("PATH_RESERVED", "That gateway path is reserved");
        boolean clash = selfId == null ? repo.existsByGatewayPathAndStatusNot(path, ApiStatus.ARCHIVED)
                : repo.existsByGatewayPathAndStatusNotAndIdNot(path, ApiStatus.ARCHIVED, selfId);
        if (clash) throw ApiException.conflict("PATH_IN_USE", "Another API already uses this gateway path");
        // 3. policy checks
        if (r.rateLimitEnabled() && r.rateLimitBurst() > r.rateLimitRequests() * 10) throw ApiException.badRequest("BURST_TOO_LARGE", "Burst may be at most 10x the request limit");
        Set<String> headers = new LinkedHashSet<>();
        if (r.allowedHeaders() != null) for (String h : r.allowedHeaders()) {
            String n = h.trim().toLowerCase();
            if (!n.matches("^[a-z0-9-]{1,64}$")) throw ApiException.badRequest("HEADER_INVALID", "Invalid header name: " + h);
            if (RESERVED_HEADERS.contains(n)) throw ApiException.badRequest("HEADER_RESERVED", "Header '" + n + "' is managed by the gateway and cannot be allow-listed");
            headers.add(n);
        }
        Set<String> origins = new LinkedHashSet<>();
        if (r.corsOrigins() != null) for (String o : r.corsOrigins()) {
            if (!o.matches("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$")) throw ApiException.badRequest("CORS_INVALID", "CORS origins must be exact origins such as https://app.example.com (wildcards are not allowed)");
            origins.add(o);
        }
        a.setName(r.name().trim()); a.setDescription(r.description()); a.setVersion(r.version()); a.setEnvironment(r.environment());
        a.setGatewayPath(path); a.setUpstreamUrl(r.upstreamUrl().trim());
        a.setMethods(r.methods().stream().sorted().collect(Collectors.joining(",")));
        a.setAuthPolicy(r.authPolicy());
        a.setRateLimitEnabled(r.rateLimitEnabled()); a.setRateLimitRequests(r.rateLimitRequests());
        a.setRateLimitWindowSeconds(r.rateLimitWindowSeconds()); a.setRateLimitBurst(r.rateLimitBurst());
        a.setConnectTimeoutMs(r.connectTimeoutMs()); a.setResponseTimeoutMs(r.responseTimeoutMs());
        a.setMaxRequestBytes(r.maxRequestBytes());
        a.setAllowedHeaders(headers.isEmpty() ? null : String.join(",", headers));
        a.setCorsOrigins(origins.isEmpty() ? null : String.join(",", origins));
        a.setUpdatedAt(Instant.now());
    }
}
