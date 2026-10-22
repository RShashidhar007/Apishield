package io.apishield.gateway;

import io.apishield.common.key.ApiKeyCodec;
import io.apishield.common.model.KeyConfig;
import io.apishield.common.model.Outcomes;
import io.apishield.common.model.RouteConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

/**
 * API keys and JWTs are separate mechanisms with explicit per-API policies:
 *  API_KEY -> X-API-Key header only; JWT -> Authorization: Bearer only; NONE -> anonymous.
 * Clients always receive the same generic 401 for any credential problem; the precise reason goes to analytics only.
 */
@Component
public class Authenticator {
    public record Principal(Long keyId, Long userId, Integer quotaOverride) {}
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final GatewayProperties props; private final SecretKey jwtKey;
    public Authenticator(GatewayProperties props) {
        this.props = props;
        this.jwtKey = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Principal authenticate(RouteConfig route, HttpHeaders headers, String method, ConfigStore.Snapshot snap) {
        return switch (route.authPolicy()) {
            case "NONE" -> new Principal(null, null, null);
            case "JWT" -> jwt(headers);
            case "API_KEY" -> apiKey(route, headers, method, snap);
            default -> throw fail("UNSUPPORTED_POLICY");
        };
    }

    private Principal apiKey(RouteConfig route, HttpHeaders h, String method, ConfigStore.Snapshot snap) {
        String raw = h.getFirst("X-API-Key");
        if (raw == null || raw.isBlank()) throw fail("MISSING_CREDENTIALS");
        var prefix = ApiKeyCodec.extractPrefix(raw);
        // Always compute the keyed hash so unknown-prefix and wrong-secret paths cost the same.
        String computed = ApiKeyCodec.hash(raw.length() > 128 ? raw.substring(0, 128) : raw, props.getApiKeyPepper());
        if (prefix.isEmpty()) throw fail("INVALID_API_KEY");
        KeyConfig k = snap.keysByPrefix().get(prefix.get());
        if (k == null || !java.security.MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), k.hash().getBytes(StandardCharsets.UTF_8))) throw fail("INVALID_API_KEY");
        if (k.revoked()) throw fail("REVOKED_API_KEY", k.id());
        if (k.expiresAt() != null && k.expiresAt().isBefore(Instant.now())) throw fail("EXPIRED_API_KEY", k.id());
        if (!k.apiIds().contains(route.id())) throw new GatewayException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This credential is not permitted to access this API", Outcomes.FORBIDDEN, "KEY_NOT_AUTHORIZED_FOR_API");
        String need = SAFE_METHODS.contains(method) ? "READ" : "WRITE";
        if (!k.scopes().contains(need)) throw new GatewayException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This credential does not have the required scope", Outcomes.FORBIDDEN, "INSUFFICIENT_SCOPE");
        return new Principal(k.id(), k.ownerUserId(), k.quotaRequests());
    }

    private Principal jwt(HttpHeaders h) {
        String a = h.getFirst(HttpHeaders.AUTHORIZATION);
        if (a == null || !a.startsWith("Bearer ")) throw fail("MISSING_CREDENTIALS");
        try {
            Claims c = Jwts.parser().verifyWith(jwtKey).requireIssuer(props.getJwt().getIssuer()).requireAudience(props.getJwt().getAudience())
                    .build().parseSignedClaims(a.substring(7).trim()).getPayload();
            return new Principal(null, Long.valueOf(c.getSubject()), null);
        } catch (JwtException | IllegalArgumentException e) { throw fail("INVALID_JWT"); }
    }

    private static GatewayException fail(String reason) { return fail(reason, null); }
    private static GatewayException fail(String reason, Long keyId) {
        return new GatewayException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Missing or invalid credentials", Outcomes.AUTH_FAILED, reason);
    }
}
