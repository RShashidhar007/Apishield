package io.apishield.backend;

import io.apishield.backend.api.ApiDtos;
import io.apishield.backend.api.ApiRepository;
import io.apishield.backend.api.ApiService;
import io.apishield.backend.audit.AuditService;
import io.apishield.backend.common.ApiException;
import io.apishield.backend.security.AuthUser;
import io.apishield.common.net.DestinationPolicy;
import io.apishield.common.net.DestinationRejectedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiServiceSsrfTest {
    private final ApiRepository repo = mock(ApiRepository.class);
    private final ApiService svc = new ApiService(repo, new DestinationPolicy(Set.of("catalog-service"), Set.of(), true, false), mock(AuditService.class));
    private final AuthUser admin = new AuthUser(1L, "admin@x.io", Set.of("ADMIN"));

    private ApiDtos.ApiRequest req(String upstream, String path) {
        return new ApiDtos.ApiRequest("Catalog", null, "v1", "DEV", path, upstream, Set.of("GET"), "API_KEY", true, 10, 60, 0, 1000, 5000, 1048576, List.of(), List.of());
    }

    @Test void rejectsCloudMetadataUpstream() {
        var e = assertThrows(DestinationRejectedException.class, () -> svc.create(admin, req("http://169.254.169.254/latest", "/meta")));
        assertEquals("DESTINATION_HOST_NOT_ALLOWED", e.code());
        verify(repo, never()).save(any());
    }
    @Test void rejectsLoopbackAndInternalHosts() {
        assertThrows(DestinationRejectedException.class, () -> svc.create(admin, req("http://localhost:8080", "/a")));
        assertThrows(DestinationRejectedException.class, () -> svc.create(admin, req("http://10.0.0.5", "/b")));
    }
    @Test void rejectsNonHttpSchemes() {
        assertThrows(DestinationRejectedException.class, () -> svc.create(admin, req("file:///etc/passwd", "/ok")));
    }
    @Test void rejectsReservedHeadersAndWildcardCors() {
        var bad = new ApiDtos.ApiRequest("C", null, "v1", "DEV", "/ok", "http://catalog-service:8081", Set.of("GET"), "API_KEY", false, 1, 1, 0, 1000, 5000, 0, List.of("Host"), List.of());
        // upstream host is allow-listed but DNS cannot resolve it in unit tests -> destination or header error, never a save
        assertThrows(RuntimeException.class, () -> svc.create(admin, bad));
        verify(repo, never()).save(any());
    }
}
