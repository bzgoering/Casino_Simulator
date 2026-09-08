package com.casino.security;

import com.casino.config.CasinoProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the authentication endpoints per client address.
 *
 * <p>Sign-in is the one place an attacker can guess at a secret, so it is rate limited
 * independently of the per-account lockout: the lockout protects one account from many guesses,
 * this protects the whole system from one attacker spraying many accounts.
 *
 * <p>The bucket map is swept on a timer rather than emptied when it fills. Emptying it would
 * discard every client's consumed tokens at once, so anyone able to create enough distinct
 * addresses could reset their own limit on demand by pushing the map over its cap. Sweeping only
 * idle entries is lossless instead: a bucket refills completely within its window, so one that
 * has not been touched for longer than that is indistinguishable from a fresh one.
 *
 * <p>This keys on the address the servlet container reports. Behind the shipped nginx that is the
 * real client only because {@code server.forward-headers-strategy} is set, which is what lets
 * Tomcat resolve {@code X-Forwarded-For} from a trusted proxy. Without it every request would
 * arrive from the proxy's own address and the whole system would share one bucket.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final int MAX_TRACKED_CLIENTS = 50_000;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    /** Idle for two full windows: the bucket has certainly refilled, so dropping it loses nothing. */
    private static final Duration IDLE_BEFORE_EVICTION = WINDOW.multipliedBy(2);

    private final Map<String, Tracked> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public AuthRateLimitFilter(CasinoProperties properties) {
        this.requestsPerMinute = properties.security().authRatePerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the credential-handling endpoints need this; game traffic is already authenticated.
        // A CORS preflight carries no credentials and is not an attempt at one, so it does not
        // spend a token -- otherwise a browser's own preflights would eat into the player's limit.
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Bucket bucket = bucketFor(clientKey(request));
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"error\":\"TOO_MANY_REQUESTS\","
                        + "\"message\":\"Too many attempts. Please slow down and try again shortly.\"}");
    }

    private Bucket bucketFor(String key) {
        Tracked tracked = buckets.get(key);
        if (tracked == null) {
            if (buckets.size() >= MAX_TRACKED_CLIENTS) {
                evictIdle();
                trimToCap();
            }
            tracked = buckets.computeIfAbsent(key, k -> new Tracked(newBucket()));
        }
        tracked.lastSeen = System.nanoTime();
        return tracked.bucket;
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, WINDOW)
                        .build())
                .build();
    }

    /** Drops buckets nobody has used for long enough that they have certainly refilled. */
    @Scheduled(fixedDelayString = "PT1M")
    public void evictIdle() {
        long cutoff = System.nanoTime() - IDLE_BEFORE_EVICTION.toNanos();
        int before = buckets.size();
        buckets.values().removeIf(tracked -> tracked.lastSeen < cutoff);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("Evicted {} idle rate-limit bucket(s); {} remain", removed, buckets.size());
        }
    }

    /**
     * Last resort when the map is still at its cap after a sweep: drop the least recently seen
     * entries. Bounded and targeted, rather than clearing the map and handing every active client
     * -- including whoever caused the overflow -- a fresh allowance.
     */
    private void trimToCap() {
        int excess = buckets.size() - MAX_TRACKED_CLIENTS + 1;
        if (excess <= 0) {
            return;
        }
        List<Map.Entry<String, Tracked>> oldest = buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastSeen))
                .limit(excess)
                .toList();
        oldest.forEach(entry -> buckets.remove(entry.getKey(), entry.getValue()));
        log.warn("Rate-limit map at capacity; evicted {} least recently seen bucket(s)", oldest.size());
    }

    /**
     * The address the container reports for the request. With
     * {@code server.forward-headers-strategy} configured this is the real client as resolved from
     * a trusted proxy's headers; without a proxy it is the peer address. It is never read straight
     * off an attacker-supplied header here.
     */
    private static String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }

    /** A bucket plus when it was last used, so the sweeper can tell idle from active. */
    private static final class Tracked {
        private final Bucket bucket;
        private volatile long lastSeen;

        private Tracked(Bucket bucket) {
            this.bucket = bucket;
            this.lastSeen = System.nanoTime();
        }
    }
}
