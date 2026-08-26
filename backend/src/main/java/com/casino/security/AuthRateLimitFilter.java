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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the authentication endpoints per client address.
 *
 * <p>Sign-in is the one place an attacker can guess at a secret, so it is rate limited
 * independently of the per-account lockout: the lockout protects one account from many guesses,
 * this protects the whole system from one attacker spraying many accounts.
 *
 * <p>The bucket map is capped and swept, so the limiter cannot itself become a memory
 * exhaustion vector.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public AuthRateLimitFilter(CasinoProperties properties) {
        this.requestsPerMinute = properties.security().authRatePerMinute();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the credential-handling endpoints need this; game traffic is already authenticated.
        return !request.getRequestURI().startsWith(AUTH_PATH_PREFIX);
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
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            buckets.clear();
        }
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build());
    }

    /**
     * The remote address, not a forwarded header. {@code X-Forwarded-For} is attacker-controlled
     * unless a trusted proxy has rewritten it, and trusting it here would let anyone bypass the
     * limit by varying one header. Behind a real proxy, configure {@code server.forward-headers-strategy}
     * so the container populates the remote address correctly instead.
     */
    private static String clientKey(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null ? "unknown" : address;
    }
}
