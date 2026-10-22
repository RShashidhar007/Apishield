package io.apishield.gateway;

import io.apishield.common.model.RouteConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.awaitility.Awaitility.await;

/** Connection-time SSRF defence: the host is allow-listed but resolves to loopback, which the policy forbids, so the connect is refused. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "gateway.destination.allow-loopback=false")
@AutoConfigureWebTestClient(timeout = "10000")
@ActiveProfiles("test")
class GatewayDestinationBlockedTest {
    @Autowired WebTestClient web; @Autowired ConfigStore store; @Autowired ApplicationEventPublisher publisher;
    @MockBean RateLimiter limiter;

    @Test void connectTimeValidationBlocksForbiddenResolvedAddress() {
        store.replace("t2", List.of(new RouteConfig(3, "ssrf", "v1", "DEV", "/ssrf", "http://localhost:9/", Set.of("GET"), "NONE", false, 1, 1, 0, 1000, 1000, 100, List.of(), List.of())), List.of());
        publisher.publishEvent(new RefreshRoutesEvent(this));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                web.get().uri("/gateway/ssrf/x").exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("DESTINATION_BLOCKED"));
    }
}
