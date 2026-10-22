package io.apishield.backend.api;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class ApiDtos {
    private ApiDtos() {}

    public record ApiRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,32}$", message = "Version may contain letters, digits, '.', '_' and '-'") String version,
            @NotBlank @Pattern(regexp = "DEV|STAGING|PROD") String environment,
            @NotBlank @Pattern(regexp = "^/[a-z0-9][a-z0-9_-]*(/[a-z0-9][a-z0-9_-]*){0,5}$", message = "Path must look like /catalog or /catalog/v1 (lower case, no trailing slash)") String gatewayPath,
            @NotBlank @Size(max = 500) String upstreamUrl,
            @NotEmpty Set<@Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String> methods,
            @NotBlank @Pattern(regexp = "API_KEY|JWT|NONE") String authPolicy,
            boolean rateLimitEnabled,
            @Min(1) @Max(100000) int rateLimitRequests,
            @Min(1) @Max(86400) int rateLimitWindowSeconds,
            @Min(0) @Max(100000) int rateLimitBurst,
            @Min(100) @Max(10000) int connectTimeoutMs,
            @Min(100) @Max(60000) int responseTimeoutMs,
            @Min(0) @Max(52428800) long maxRequestBytes,
            List<@Size(max = 64) String> allowedHeaders,
            List<@Size(max = 200) String> corsOrigins) {}

    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    public record ApiView(Long id, String name, String description, String version, String environment, String status,
                          String gatewayPath, String publicPath, String upstreamUrl, Set<String> methods, String authPolicy,
                          boolean rateLimitEnabled, int rateLimitRequests, int rateLimitWindowSeconds, int rateLimitBurst,
                          int connectTimeoutMs, int responseTimeoutMs, long maxRequestBytes,
                          List<String> allowedHeaders, List<String> corsOrigins, Long ownerId, Instant createdAt, Instant updatedAt) {
        /** Developers get the catalog view without the internal upstream address. */
        public static ApiView of(ApiDefinition a, boolean includeUpstream) {
            return new ApiView(a.getId(), a.getName(), a.getDescription(), a.getVersion(), a.getEnvironment(), a.getStatus().name(),
                    a.getGatewayPath(), "/gateway" + a.getGatewayPath(), includeUpstream ? a.getUpstreamUrl() : null, a.methodSet(), a.getAuthPolicy(),
                    a.isRateLimitEnabled(), a.getRateLimitRequests(), a.getRateLimitWindowSeconds(), a.getRateLimitBurst(),
                    a.getConnectTimeoutMs(), a.getResponseTimeoutMs(), a.getMaxRequestBytes(), a.headerList(), a.originList(),
                    includeUpstream ? a.getOwnerId() : null, a.getCreatedAt(), a.getUpdatedAt());
        }
    }
}
