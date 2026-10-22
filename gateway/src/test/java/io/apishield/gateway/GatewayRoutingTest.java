package io.apishield.gateway;

import com.sun.net.httpserver.HttpServer;
import io.apishield.common.key.ApiKeyCodec;
import io.apishield.common.model.KeyConfig;
import io.apishield.common.model.RouteConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Real forwarding test: an in-process upstream HTTP server, the full Spring Cloud Gateway stack, Redis limiter mocked. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gateway.destination.allow-loopback=true")
@AutoConfigureWebTestClient(timeout = "10000")
@ActiveProfiles("test")
class GatewayRoutingTest {
    static final String PEPPER = "test-pepper-test-pepper-test-pepper-123";
    static HttpServer upstream; static int port;
    static final ApiKeyCodec.Generated READ_KEY = ApiKeyCodec.generate(PEPPER);
    static final ApiKeyCodec.Generated WRONG_API_KEY = ApiKeyCodec.generate(PEPPER);
    static final ApiKeyCodec.Generated REVOKED_KEY = ApiKeyCodec.generate(PEPPER);
    static final ApiKeyCodec.Generated WRITE_KEY = ApiKeyCodec.generate(PEPPER);

    @Autowired WebTestClient web; @Autowired ConfigStore store; @Autowired ApplicationEventPublisher publisher;
    @MockBean RateLimiter limiter;

    @BeforeAll static void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/v1/products", ex -> {
            String body = "{\"path\":\"" + ex.getRequestURI().getRawPath() + "\",\"query\":\"" + ex.getRequestURI().getRawQuery() + "\",\"auth\":\"" + ex.getRequestHeaders().getFirst("Authorization")
                    + "\",\"apikey\":\"" + ex.getRequestHeaders().getFirst("X-API-Key") + "\",\"xff\":\"" + ex.getRequestHeaders().getFirst("X-Forwarded-For") + "\",\"cookie\":\"" + ex.getRequestHeaders().getFirst("Cookie")
                    + "\",\"rid\":\"" + ex.getRequestHeaders().getFirst("X-Request-Id") + "\"}";
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json"); ex.getResponseHeaders().add("X-Powered-By", "Upstream"); ex.getResponseHeaders().add("X-Upstream", "yes");
            ex.sendResponseHeaders(200, b.length); ex.getResponseBody().write(b); ex.close();
        });
        upstream.createContext("/v1/slow", ex -> { try { Thread.sleep(2000); } catch (InterruptedException ignored) {} ex.sendResponseHeaders(200, -1); ex.close(); });
        upstream.start(); port = upstream.getAddress().getPort();
    }
    @AfterAll static void stop() { upstream.stop(0); }

    private void configure() {
        RouteConfig r = new RouteConfig(1, "catalog", "v1", "DEV", "/catalog", "http://127.0.0.1:" + port + "/v1", Set.of("GET", "POST"), "API_KEY", true, 5, 60, 0, 1000, 400, 100, List.of(), List.of());
        RouteConfig open = new RouteConfig(2, "open", "v1", "DEV", "/open", "http://127.0.0.1:" + port + "/v1", Set.of("GET"), "NONE", false, 1, 1, 0, 1000, 400, 100, List.of(), List.of());
        Instant future = Instant.now().plusSeconds(3600);
        store.replace("t1", List.of(r, open), List.of(
                new KeyConfig(10, READ_KEY.prefix(), READ_KEY.hash(), 5, Set.of(1L), Set.of("READ"), future, false, null),
                new KeyConfig(11, WRONG_API_KEY.prefix(), WRONG_API_KEY.hash(), 5, Set.of(99L), Set.of("READ", "WRITE"), future, false, null),
                new KeyConfig(12, REVOKED_KEY.prefix(), REVOKED_KEY.hash(), 5, Set.of(1L), Set.of("READ"), future, true, null),
                new KeyConfig(13, WRITE_KEY.prefix(), WRITE_KEY.hash(), 5, Set.of(1L), Set.of("READ", "WRITE"), future, false, null)));
        publisher.publishEvent(new RefreshRoutesEvent(this));
        when(limiter.check(anyString(), anyInt(), anyDouble(), anyInt())).thenReturn(Mono.just(new RateLimiter.Result(true, 4, 0, 5)));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> web.get().uri("/gateway/open/products").exchange().expectStatus().isOk());
    }

    @Test void forwardsRealResponseRewritesPathAndFiltersHeaders() {
        configure();
        web.get().uri("/gateway/catalog/products?limit=3").header("X-API-Key", READ_KEY.rawKey()).header("Authorization", "Bearer client-secret")
                .header("Cookie", "sid=1").header("X-Forwarded-For", "6.6.6.6").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-Id").expectHeader().doesNotExist("X-Powered-By").expectHeader().valueEquals("X-Upstream", "yes")
                .expectHeader().valueEquals("X-RateLimit-Limit", "5")
                .expectBody().jsonPath("$.path").isEqualTo("/v1/products").jsonPath("$.query").isEqualTo("limit=3")
                .jsonPath("$.auth").isEqualTo("null").jsonPath("$.apikey").isEqualTo("null").jsonPath("$.cookie").isEqualTo("null")
                .jsonPath("$.xff").value(v -> { if ("6.6.6.6".equals(v) || String.valueOf(v).contains("6.6.6.6")) throw new AssertionError("client XFF leaked: " + v); });
    }
    @Test void rejectsMissingInvalidRevokedAndWrongApiKeys() {
        configure();
        web.get().uri("/gateway/catalog/products").exchange().expectStatus().isUnauthorized().expectBody().jsonPath("$.code").isEqualTo("INVALID_CREDENTIALS").jsonPath("$.correlationId").exists();
        web.get().uri("/gateway/catalog/products").header("X-API-Key", "ask_aaaaaaaaaaaa_notarealsecretnotarealsecret").exchange().expectStatus().isUnauthorized();
        web.get().uri("/gateway/catalog/products").header("X-API-Key", REVOKED_KEY.rawKey()).exchange().expectStatus().isUnauthorized();
        web.get().uri("/gateway/catalog/products").header("X-API-Key", WRONG_API_KEY.rawKey()).exchange().expectStatus().isForbidden();
    }
    @Test void enforcesMethodRestrictionAndScopes() {
        configure();
        web.delete().uri("/gateway/catalog/products").header("X-API-Key", READ_KEY.rawKey()).exchange().expectStatus().isEqualTo(405).expectHeader().exists("Allow");
        web.post().uri("/gateway/catalog/products").header("X-API-Key", READ_KEY.rawKey()).bodyValue("{}").exchange().expectStatus().isForbidden(); // READ scope cannot POST
    }
    @Test void enforcesRequestSizeLimitByDeclaredAndStreamedLength() {
        configure();
        web.post().uri("/gateway/catalog/products").header("X-API-Key", WRITE_KEY.rawKey()).bodyValue("x".repeat(500)).exchange()
                .expectStatus().isEqualTo(413).expectBody().jsonPath("$.code").isEqualTo("PAYLOAD_TOO_LARGE");
        web.post().uri("/gateway/catalog/products").header("X-API-Key", WRITE_KEY.rawKey()).header("Transfer-Encoding", "chunked")
                .body(reactor.core.publisher.Flux.just("x".repeat(60), "x".repeat(60)), String.class).exchange().expectStatus().isEqualTo(413);
    }
    @Test void rateLimitedRequestsGet429WithRetryAfter() {
        configure();
        when(limiter.check(anyString(), anyInt(), anyDouble(), anyInt())).thenReturn(Mono.just(new RateLimiter.Result(false, 0, 2500, 5)));
        web.get().uri("/gateway/catalog/products").header("X-API-Key", READ_KEY.rawKey()).exchange().expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "3").expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
    }
    @Test void failsClosedWhenRedisIsUnavailable() {
        configure();
        when(limiter.check(anyString(), anyInt(), anyDouble(), anyInt())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        web.get().uri("/gateway/catalog/products").header("X-API-Key", READ_KEY.rawKey()).exchange().expectStatus().isEqualTo(503).expectBody().jsonPath("$.code").isEqualTo("RATE_LIMIT_UNAVAILABLE");
    }
    @Test void slowUpstreamReturns504() {
        configure();
        web.get().uri("/gateway/open/slow").exchange().expectStatus().isEqualTo(504).expectBody().jsonPath("$.code").isEqualTo("UPSTREAM_TIMEOUT");
    }
    @Test void unknownRouteAndTraversalAreRejected() {
        configure();
        web.get().uri("/gateway/nothing/here").exchange().expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND");
        web.get().uri("/gateway/open/%2e%2e/admin").exchange().expectStatus().isBadRequest();
        web.get().uri("/gateway/open/a%2fb").exchange().expectStatus().isBadRequest();
    }
    @Test void errorResponsesNeverLeakInternals() {
        configure();
        String body = new String(web.get().uri("/gateway/catalog/products").exchange().expectBody().returnResult().getResponseBodyContent(), StandardCharsets.UTF_8);
        if (body.contains("Exception") || body.contains("localhost") || body.contains("at io.")) throw new AssertionError("internal detail leaked: " + body);
    }
}
