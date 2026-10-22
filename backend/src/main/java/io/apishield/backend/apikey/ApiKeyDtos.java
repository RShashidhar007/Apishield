package io.apishield.backend.apikey;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Set;

public final class ApiKeyDtos {
    private ApiKeyDtos() {}
    public record CreateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
                                @NotEmpty @Size(max = 50) Set<Long> apiIds,
                                @NotEmpty Set<@Pattern(regexp = "READ|WRITE") String> scopes,
                                @Min(1) @Max(365) Integer expiresInDays,
                                @Min(1) @Max(1000000) Integer quotaRequests) {}
    public record KeyView(Long id, String name, String description, String prefix, String maskedKey, Set<Long> apiIds, Set<String> scopes,
                          Instant expiresAt, String status, Instant revokedAt, Integer quotaRequests, Instant lastUsedAt, Instant createdAt,
                          long requests24h, Long ownerId) {}
    /** The raw key is present ONLY in this response and is never retrievable again. */
    public record KeyWithSecret(KeyView key, String rawKey) {}
}
