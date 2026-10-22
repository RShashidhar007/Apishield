package io.apishield.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/** Needs Docker (Testcontainers). Skipped automatically when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisRateLimiter limiter() {
        var f = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379))); f.afterPropertiesSet();
        return new RedisRateLimiter(new ReactiveStringRedisTemplate(f), new GatewayProperties());
    }

    @Test void allowsUpToCapacityThenRejectsWithRetryAfter() {
        var l = limiter(); String key = "t:" + System.nanoTime();
        for (int i = 0; i < 3; i++) assertTrue(l.check(key, 3, 0.0001, 3).block().allowed(), "request " + i);
        var denied = l.check(key, 3, 0.0001, 3).block();
        assertFalse(denied.allowed()); assertTrue(denied.retryAfterMs() > 0);
    }
    @Test void isAtomicUnderConcurrency() {
        var l = limiter(); String key = "c:" + System.nanoTime();
        long allowed = java.util.stream.IntStream.range(0, 200).parallel().filter(i -> l.check(key, 50, 0.00001, 50).block().allowed()).count();
        assertEquals(50, allowed, "exactly the bucket capacity may pass, never more");
    }
    @Test void bucketsAreIndependentPerKey() {
        var l = limiter(); String a = "a:" + System.nanoTime(), b = "b:" + System.nanoTime();
        assertTrue(l.check(a, 1, 0.00001, 1).block().allowed()); assertFalse(l.check(a, 1, 0.00001, 1).block().allowed());
        assertTrue(l.check(b, 1, 0.00001, 1).block().allowed());
    }
}
