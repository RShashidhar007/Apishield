package io.apishield.gateway;

import reactor.core.publisher.Mono;

public interface RateLimiter {
    /** allowed, remaining tokens, and milliseconds until the next token when rejected. */
    record Result(boolean allowed, long remaining, long retryAfterMs, int limit) {}

    /** @param capacity bucket size (requests + burst)  @param refillPerMs tokens added per millisecond. Errors propagate: the caller decides the failure policy. */
    Mono<Result> check(String key, int capacity, double refillPerMs, int limit);
}
