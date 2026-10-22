package io.apishield.backend;

import io.apishield.backend.config.AppProperties;
import io.apishield.backend.security.JwtService;
import io.apishield.backend.user.RoleEntity;
import io.apishield.backend.user.RoleName;
import io.apishield.backend.user.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static AppProperties props(String secret, String aud, Duration ttl) {
        AppProperties p = new AppProperties();
        p.getJwt().setSecret(secret); p.getJwt().setIssuer("apishield"); p.getJwt().setAudience(aud); p.getJwt().setAccessTtl(ttl);
        return p;
    }
    private static UserAccount user() {
        UserAccount u = new UserAccount(); u.setId(7L); u.setEmail("a@b.c");
        RoleEntity r = new RoleEntity(); r.setName(RoleName.DEVELOPER); u.getRoles().add(r);
        return u;
    }
    private static final String SECRET = "unit-secret-unit-secret-unit-secret-123";

    @Test void roundTrip() {
        JwtService s = new JwtService(props(SECRET, "aud", Duration.ofMinutes(5)));
        var p = s.parse(s.issue(user())).orElseThrow();
        assertEquals(7L, p.id()); assertTrue(p.has("DEVELOPER"));
    }
    @Test void rejectsExpired() {
        JwtService s = new JwtService(props(SECRET, "aud", Duration.ofSeconds(-5)));
        assertTrue(s.parse(s.issue(user())).isEmpty());
    }
    @Test void rejectsTamperedAndWrongKey() {
        JwtService a = new JwtService(props(SECRET, "aud", Duration.ofMinutes(5)));
        JwtService b = new JwtService(props("another-secret-another-secret-another-1", "aud", Duration.ofMinutes(5)));
        assertTrue(b.parse(a.issue(user())).isEmpty());
        String t = a.issue(user());
        assertTrue(a.parse(t.substring(0, t.length() - 2) + "xx").isEmpty());
    }
    @Test void rejectsWrongAudienceAndGarbage() {
        JwtService a = new JwtService(props(SECRET, "aud-one", Duration.ofMinutes(5)));
        JwtService b = new JwtService(props(SECRET, "aud-two", Duration.ofMinutes(5)));
        assertTrue(b.parse(a.issue(user())).isEmpty());
        assertTrue(a.parse("not-a-jwt").isEmpty());
        assertTrue(a.parse("").isEmpty());
    }
}
