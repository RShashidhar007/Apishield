package io.apishield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeaderPolicyTest {
    @Test void stripsCredentialsAndForwardedHeadersAndAddsCorrelationId() {
        HttpHeaders h = new HttpHeaders();
        h.add("Authorization", "Bearer secret"); h.add("X-API-Key", "ask_x"); h.add("Cookie", "s=1"); h.add("Host", "evil");
        h.add("X-Forwarded-For", "6.6.6.6"); h.add("Forwarded", "for=6.6.6.6"); h.add("X-Real-IP", "6.6.6.6"); h.add("Transfer-Encoding", "chunked");
        h.add("Accept", "application/json"); h.add("Content-Type", "application/json"); h.add("X-Custom", "ok");
        HeaderPolicy.sanitizeRequest(h, List.of("X-Custom"), false, "corr-12345678");
        for (String n : List.of("Authorization", "X-API-Key", "Cookie", "Host", "X-Forwarded-For", "Forwarded", "X-Real-IP", "Transfer-Encoding")) assertFalse(h.containsKey(n), n + " must be dropped");
        assertEquals("application/json", h.getFirst("Accept"));
        assertEquals("ok", h.getFirst("X-Custom"));
        assertEquals("corr-12345678", h.getFirst("X-Request-Id"));
    }
    @Test void authorizationOnlyForwardedForAnonymousApis() {
        HttpHeaders h = new HttpHeaders(); h.add("Authorization", "Basic abc");
        HeaderPolicy.sanitizeRequest(h, List.of(), true, "corr-12345678");
        assertEquals("Basic abc", h.getFirst("Authorization"));
    }
    @Test void reservedHeadersCannotBeAllowListedPerRoute() {
        HttpHeaders h = new HttpHeaders(); h.add("X-Forwarded-For", "1.1.1.1");
        HeaderPolicy.sanitizeRequest(h, List.of("x-forwarded-for"), false, "corr-12345678");
        assertFalse(h.containsKey("X-Forwarded-For"));
    }
    @Test void responseInfrastructureHeadersRemoved() {
        HttpHeaders h = new HttpHeaders(); h.add("Server", "nginx/1.2"); h.add("X-Powered-By", "Express"); h.add("Content-Type", "text/plain");
        HeaderPolicy.sanitizeResponse(h, "corr-12345678");
        assertFalse(h.containsKey("Server")); assertFalse(h.containsKey("X-Powered-By"));
        assertEquals("corr-12345678", h.getFirst("X-Request-Id"));
    }
}
