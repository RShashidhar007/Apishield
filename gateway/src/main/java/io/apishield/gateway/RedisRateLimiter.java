package io.apishield.gateway;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Distributed token bucket executed atomically in Redis (Lua), so every gateway instance shares one counter per key.
 * Time comes from the Redis server clock (TIME), avoiding clock skew between gateways.
 */
@Component("apiShieldRedisRateLimiter")
public class RedisRateLimiter implements RateLimiter {
    static final String LUA = """
        local capacity = tonumber(ARGV[1])
        local refill = tonumber(ARGV[2])
        local t = redis.call('TIME')
        local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
        local d = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
        local tokens = tonumber(d[1])
        local ts = tonumber(d[2])
        if tokens == nil then tokens = capacity; ts = now end
        local delta = math.max(0, now - ts)
        tokens = math.min(capacity, tokens + delta * refill)
        local allowed = 0
        if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
        redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
        redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / refill) + 1000)
        local retry = 0
        if allowed == 0 then retry = math.ceil((1 - tokens) / refill) end
        return {allowed, math.floor(tokens), retry}
        """;

    private final ReactiveStringRedisTemplate redis; private final GatewayProperties props;
    @SuppressWarnings("rawtypes") private final DefaultRedisScript<List> script = new DefaultRedisScript<>(LUA, List.class);

    public RedisRateLimiter(ReactiveStringRedisTemplate redis, GatewayProperties props) { this.redis = redis; this.props = props; }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Result> check(String key, int capacity, double refillPerMs, int limit) {
        return redis.execute(script, List.of(key), List.of(String.valueOf(capacity), String.valueOf(refillPerMs)))
                .next()
                .timeout(Duration.ofMillis(props.getRateLimitRedisTimeoutMs()))
                .map(r -> {
                    List<Object> l = (List<Object>) r;
                    return new Result(((Number) l.get(0)).longValue() == 1, ((Number) l.get(1)).longValue(), ((Number) l.get(2)).longValue(), limit);
                });
    }
}
