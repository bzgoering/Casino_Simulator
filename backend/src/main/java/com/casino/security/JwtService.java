package com.casino.security;

import com.casino.config.CasinoProperties;
import com.casino.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the HS256 bearer tokens used for all authenticated calls.
 *
 * <p>The token carries identity only: subject, username and role. It deliberately does
 * <em>not</em> carry a balance. A balance in a signed token would still be replayable, letting a
 * player re-present a stale token from before they lost, so authoritative balance always comes
 * from the database or the server-side guest session.
 *
 * <p>The signing key must be supplied out of band. Outside the dev profile a missing key aborts
 * startup rather than falling back to anything predictable.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey signingKey;
    private final CasinoProperties properties;

    public JwtService(CasinoProperties properties, Environment environment) {
        this.properties = properties;
        this.signingKey = resolveSigningKey(properties.jwt().secret(), environment);
    }

    private static SecretKey resolveSigningKey(String configured, Environment environment) {
        if (configured != null && !configured.isBlank()) {
            byte[] keyBytes = configured.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "casino.jwt.secret must be at least 32 bytes for HS256; got " + keyBytes.length);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        }
        boolean devProfile = environment.matchesProfiles("dev");
        if (!devProfile) {
            throw new IllegalStateException(
                    "casino.jwt.secret is not set. Provide CASINO_JWT_SECRET (32+ bytes) before starting.");
        }
        // Dev convenience: a fresh random key each boot. Tokens do not survive a restart, and
        // nothing predictable is ever committed to the repository.
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        log.warn("No casino.jwt.secret configured; generated an ephemeral dev key. "
                + "Tokens will be invalidated on restart. Set CASINO_JWT_SECRET for anything real.");
        log.debug("Ephemeral dev key fingerprint: {}",
                Base64.getEncoder().encodeToString(generated).substring(0, 8));
        return Keys.hmacShaKeyFor(generated);
    }

    public String issue(CasinoPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().ttl());
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(principal.subject())
                .claim(CLAIM_ROLE, principal.role().name())
                .claim(CLAIM_USERNAME, principal.username())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies the signature and expiry and returns the caller, or empty if the token is not
     * trustworthy. Never throws on bad input: a malformed token is an authentication failure,
     * not a server error.
     */
    public java.util.Optional<CasinoPrincipal> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
            String username = claims.get(CLAIM_USERNAME, String.class);
            return java.util.Optional.of(new CasinoPrincipal(claims.getSubject(), username, role));
        } catch (JwtException | IllegalArgumentException e) {
            // Logged at debug only: a failed token must not fill the logs on an attack.
            log.debug("Rejected token: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public java.time.Duration ttl() {
        return properties.jwt().ttl();
    }
}
