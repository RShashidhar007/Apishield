package io.apishield.backend.auth;

import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.config.AppProperties;
import io.apishield.backend.security.JwtService;
import io.apishield.backend.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private static final SecureRandom RNG = new SecureRandom();
    private final UserRepository users; private final RoleRepository roles; private final RefreshTokenRepository tokens;
    private final PasswordEncoder encoder; private final JwtService jwt; private final AppProperties props; private final AuditService audit;
    /** Used to equalise timing when the account does not exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, RoleRepository roles, RefreshTokenRepository tokens, PasswordEncoder encoder, JwtService jwt, AppProperties props, AuditService audit) {
        this.users = users; this.roles = roles; this.tokens = tokens; this.encoder = encoder; this.jwt = jwt; this.props = props; this.audit = audit;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserDtos.UserView register(AuthDtos.RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        UserAccount u = newUser(email, r.displayName().trim(), r.password(), Set.of(RoleName.DEVELOPER));
        audit.asActor(u.getId(), email, "USER_REGISTERED", "USER", String.valueOf(u.getId()), "SUCCESS", null);
        return UserDtos.UserView.of(u);
    }

    @Transactional
    public UserAccount newUser(String email, String name, String password, Set<RoleName> rs) {
        UserAccount u = new UserAccount();
        u.setEmail(email); u.setDisplayName(name); u.setPasswordHash(encoder.encode(password));
        u.setCreatedAt(Instant.now()); u.setUpdatedAt(Instant.now());
        rs.forEach(x -> u.getRoles().add(roles.findByName(x).orElseThrow()));
        return users.save(u);
    }

    /** noRollbackFor: failed-attempt counters must persist even though we throw. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest r) {
        Instant now = Instant.now();
        UserAccount u = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
        if (u == null) {
            encoder.matches(r.password(), dummyHash);
            audit.asActor(null, r.email().trim(), "LOGIN", "USER", null, "FAILURE", "unknown account");
            throw invalid();
        }
        if (u.locked(now)) {
            audit.asActor(u.getId(), u.getEmail(), "LOGIN", "USER", String.valueOf(u.getId()), "DENIED", "account locked");
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account temporarily locked after repeated failures. Try again later.");
        }
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            int n = u.getFailedAttempts() + 1;
            u.setFailedAttempts(n);
            if (n >= props.getLogin().getMaxFailedAttempts()) {
                u.setLockedUntil(now.plusSeconds(props.getLogin().getLockMinutes() * 60L)); u.setFailedAttempts(0);
                audit.asActor(u.getId(), u.getEmail(), "ACCOUNT_LOCKED", "USER", String.valueOf(u.getId()), "SUCCESS", "too many failed logins");
            }
            audit.asActor(u.getId(), u.getEmail(), "LOGIN", "USER", String.valueOf(u.getId()), "FAILURE", "bad password");
            throw invalid();
        }
        if (!u.isActive()) {
            audit.asActor(u.getId(), u.getEmail(), "LOGIN", "USER", String.valueOf(u.getId()), "DENIED", "account deactivated");
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "This account has been deactivated");
        }
        u.setFailedAttempts(0); u.setLockedUntil(null); u.setLastLoginAt(now);
        audit.asActor(u.getId(), u.getEmail(), "LOGIN", "USER", String.valueOf(u.getId()), "SUCCESS", null);
        return issue(u, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.TokenResponse refresh(String raw) {
        RefreshToken t = tokens.findByTokenHash(sha256(raw)).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        if (t.isUsed() || t.isRevoked()) {
            // Replay of a rotated token: assume theft and revoke the whole family.
            tokens.revokeFamily(t.getFamilyId());
            audit.asActor(t.getUserId(), null, "REFRESH_TOKEN_REPLAY", "USER", String.valueOf(t.getUserId()), "DENIED", "token family revoked");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED", "Session revoked. Please sign in again.");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Session expired");
        UserAccount u = users.findById(t.getUserId()).filter(UserAccount::isActive).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token"));
        t.setUsed(true);
        return issue(u, t.getFamilyId());
    }

    @Transactional
    public void logout(String raw) {
        tokens.findByTokenHash(sha256(raw)).ifPresent(t -> {
            tokens.revokeFamily(t.getFamilyId());
            audit.asActor(t.getUserId(), null, "LOGOUT", "USER", String.valueOf(t.getUserId()), "SUCCESS", null);
        });
    }

    private AuthDtos.TokenResponse issue(UserAccount u, String family) {
        byte[] b = new byte[32]; RNG.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        RefreshToken t = new RefreshToken();
        t.setUserId(u.getId()); t.setTokenHash(sha256(raw)); t.setFamilyId(family);
        t.setCreatedAt(Instant.now()); t.setExpiresAt(Instant.now().plus(props.getJwt().getRefreshTtl()));
        tokens.save(t);
        return new AuthDtos.TokenResponse(jwt.issue(u), raw, jwt.accessTtlSeconds(), "Bearer", UserDtos.UserView.of(u));
    }

    private static ApiException invalid() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password"); }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
