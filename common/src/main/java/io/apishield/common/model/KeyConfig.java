package io.apishield.common.model;

import java.time.Instant;
import java.util.Set;

/** Key material visible to the gateway: lookup prefix + keyed hash only, never the raw key. */
public record KeyConfig(
        long id, String prefix, String hash, long ownerUserId,
        Set<Long> apiIds, Set<String> scopes,   // scopes: READ, WRITE
        Instant expiresAt, boolean revoked,
        Integer quotaRequests) {}              // optional per-key limit using the API's window; null = API default
