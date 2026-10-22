package io.apishield.backend.security;

import io.apishield.backend.config.AppProperties;
import io.apishield.backend.user.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Issues and validates short-lived HS256 access tokens (signature, issuer, audience and expiry are all enforced). */
@Service
public class JwtService {
    private final AppProperties props; private final SecretKey key;

    public JwtService(AppProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String issue(UserAccount u) {
        Instant now = Instant.now();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(String.valueOf(u.getId()))
                .issuer(props.getJwt().getIssuer()).audience().add(props.getJwt().getAudience()).and()
                .claim("email", u.getEmail()).claim("roles", new ArrayList<>(u.roleNames()))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(props.getJwt().getAccessTtl())))
                .signWith(key).compact();
    }

    public long accessTtlSeconds() { return props.getJwt().getAccessTtl().toSeconds(); }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(props.getJwt().getIssuer())
                    .requireAudience(props.getJwt().getAudience()).build().parseSignedClaims(token).getPayload();
            @SuppressWarnings("unchecked") List<String> roles = c.get("roles", List.class);
            return Optional.of(new AuthUser(Long.valueOf(c.getSubject()), c.get("email", String.class), new HashSet<>(roles == null ? List.of() : roles)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
