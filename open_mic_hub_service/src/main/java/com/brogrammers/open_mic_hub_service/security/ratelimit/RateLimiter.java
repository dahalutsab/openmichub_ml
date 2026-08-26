package com.brogrammers.open_mic_hub_service.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window request counter, keyed by whatever the caller chooses (client IP, email address).
 *
 * <p>In-memory and therefore per-instance: enough to blunt online guessing against login, OTP and
 * password reset, which previously had no limit at all. Move to a shared store before running more
 * than one instance behind a load balancer.
 */
@Component
@Slf4j
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Records an attempt and reports whether it is within the limit.
     *
     * @return {@code true} if the caller may proceed
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Window current = windows.compute(key, (k, existing) ->
                existing == null || existing.startedAt().plus(window).isBefore(now)
                        ? new Window(now, new AtomicInteger(0))
                        : existing);

        int used = current.count().incrementAndGet();
        if (used > limit) {
            log.warn("Rate limit hit for key '{}' ({} attempts in {})", key, used, window);
            return false;
        }
        return true;
    }

    /** Clears the counter for a key, e.g. after a successful login. */
    public void reset(String key) {
        windows.remove(key);
    }

    /** Drops windows that have long since expired so the map does not grow without bound. */
    public void evictExpired(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
    }
}
