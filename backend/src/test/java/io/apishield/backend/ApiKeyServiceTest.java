package io.apishield.backend;

import io.apishield.backend.analytics.RequestLogRepository;
import io.apishield.backend.api.ApiDefinition;
import io.apishield.backend.api.ApiRepository;
import io.apishield.backend.api.ApiStatus;
import io.apishield.backend.apikey.ApiKey;
import io.apishield.backend.apikey.ApiKeyDtos;
import io.apishield.backend.apikey.ApiKeyRepository;
import io.apishield.backend.apikey.ApiKeyService;
import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.config.AppProperties;
import io.apishield.backend.security.AuthUser;
import io.apishield.common.key.ApiKeyCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Full key lifecycle: create (secret shown once, only hash stored) -> list (masked) -> rotate -> revoke, plus ownership and abuse limits. */
class ApiKeyServiceTest {
    private final ApiKeyRepository keys = mock(ApiKeyRepository.class);
    private final ApiRepository apis = mock(ApiRepository.class);
    private final RequestLogRepository logs = mock(RequestLogRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AppProperties props = new AppProperties();
    private ApiKeyService svc;
    private final AuthUser owner = new AuthUser(5L, "dev@x.io", Set.of("DEVELOPER"));
    private final AuthUser other = new AuthUser(6L, "other@x.io", Set.of("DEVELOPER"));

    @BeforeEach void setUp() {
        props.setApiKeyPepper("unit-test-pepper-unit-test-pepper-12");
        svc = new ApiKeyService(keys, apis, logs, props, audit);
        ApiDefinition api = new ApiDefinition(); api.setId(1L); api.setStatus(ApiStatus.ACTIVE);
        when(apis.findByIdIn(any())).thenReturn(List.of(api));
        when(keys.save(any(ApiKey.class))).thenAnswer(i -> { ApiKey k = i.getArgument(0); if (k.getId() == null) k.setId(100L + k.getPrefix().hashCode() % 7); return k; });
    }
    private ApiKeyDtos.CreateRequest req() { return new ApiKeyDtos.CreateRequest("ci", null, Set.of(1L), Set.of("READ"), 30, null); }

    @Test void createReturnsRawKeyOnceAndPersistsOnlyAHash() {
        var out = svc.create(owner, req());
        ArgumentCaptor<ApiKey> c = ArgumentCaptor.forClass(ApiKey.class); verify(keys).save(c.capture());
        ApiKey saved = c.getValue();
        assertTrue(out.rawKey().startsWith("ask_" + saved.getPrefix() + "_"));
        assertNotEquals(out.rawKey(), saved.getKeyHash());
        assertTrue(ApiKeyCodec.matches(out.rawKey(), props.getApiKeyPepper(), saved.getKeyHash()));
        assertFalse(out.key().maskedKey().contains(out.rawKey().substring(20)), "view must be masked");
        assertNotNull(saved.getExpiresAt());
        verify(audit).success(eq("API_KEY_CREATED"), eq("API_KEY"), any(), argThat(d -> !d.contains(out.rawKey())));
    }
    @Test void keysCanOnlyBeScopedToActiveExistingApis() {
        ApiDefinition inactive = new ApiDefinition(); inactive.setId(1L); inactive.setStatus(ApiStatus.INACTIVE);
        when(apis.findByIdIn(any())).thenReturn(List.of(inactive));
        assertEquals("API_NOT_AVAILABLE", assertThrows(ApiException.class, () -> svc.create(owner, req())).code());
    }
    @Test void activeKeyLimitPreventsMassCreation() {
        when(keys.countByOwnerIdAndRevokedFalse(5L)).thenReturn(20L);
        assertEquals("KEY_LIMIT", assertThrows(ApiException.class, () -> svc.create(owner, req())).code());
        verify(keys, never()).save(any());
    }
    private ApiKey existing(long owner_, boolean revoked) {
        ApiKey k = new ApiKey(); k.setId(9L); k.setOwnerId(owner_); k.setRevoked(revoked); k.setPrefix("aaaaaaaaaaaa"); k.setName("n");
        k.setApiIds(Set.of(1L)); k.setScopes(Set.of("READ")); when(keys.findById(9L)).thenReturn(Optional.of(k)); return k;
    }
    @Test void revokeMarksKeyAndAudits() {
        ApiKey k = existing(5L, false); svc.revoke(owner, 9L);
        assertTrue(k.isRevoked()); assertNotNull(k.getRevokedAt()); verify(audit).success(eq("API_KEY_REVOKED"), any(), any(), any());
    }
    @Test void rotateIssuesNewSecretAndRevokesOldImmediately() {
        ApiKey old = existing(5L, false);
        var out = svc.rotate(owner, 9L);
        assertTrue(old.isRevoked());
        assertNotEquals(old.getPrefix(), out.key().prefix());
        assertNotNull(out.rawKey());
    }
    @Test void revokedKeysCannotBeRotated() {
        existing(5L, true);
        assertEquals("KEY_REVOKED", assertThrows(ApiException.class, () -> svc.rotate(owner, 9L)).code());
    }
    @Test void otherUsersCannotSeeOrChangeAKeyAndExistenceIsNotRevealed() {
        existing(5L, false);
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> svc.revoke(other, 9L)).code());
        verify(audit).denied(eq("API_KEY_ACCESS"), any(), any(), any());
    }
    @Test void adminMayRevokeAnyKey() {
        ApiKey k = existing(5L, false); svc.revoke(new AuthUser(1L, "a@x.io", Set.of("ADMIN")), 9L); assertTrue(k.isRevoked());
    }
}
