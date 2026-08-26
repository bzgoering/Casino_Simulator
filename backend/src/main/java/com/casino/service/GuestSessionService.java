package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.Role;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory registry of guest sessions.
 *
 * <p>Nothing here touches the database, which is what satisfies "no data will be kept on guests".
 * Sessions are evicted once idle past their TTL, and the map is capped so that an attacker
 * minting guest tokens in a loop cannot exhaust heap.
 */
@Service
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);

    private final Map<String, GuestSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CasinoProperties properties;

    public GuestSessionService(CasinoProperties properties) {
        this.properties = properties;
    }

    /** Creates a guest with the standard play-money balance and returns its session. */
    public GuestSession create() {
        if (sessions.size() >= properties.guest().maxSessions()) {
            evictExpired();
        }
        if (sessions.size() >= properties.guest().maxSessions()) {
            throw new CasinoException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The casino floor is full. Please try again shortly.");
        }
        String id = newSessionId();
        GuestSession session = new GuestSession(id, new BigDecimal(Role.GUEST.startingBalance()));
        sessions.put(id, session);
        return session;
    }

    /**
     * Looks up a live session. An expired or unknown id is reported as gone rather than as a
     * generic error so the browser knows to request a fresh guest token.
     */
    public GuestSession require(String id) {
        GuestSession session = sessions.get(id);
        if (session == null || session.isExpired(properties.guest().sessionTtl())) {
            if (session != null) {
                sessions.remove(id, session);
            }
            throw new CasinoException(HttpStatus.UNAUTHORIZED,
                    "Your guest session has expired. Start a new one to keep playing.");
        }
        session.touch();
        return session;
    }

    public Optional<GuestSession> find(String id) {
        return Optional.ofNullable(sessions.get(id))
                .filter(s -> !s.isExpired(properties.guest().sessionTtl()));
    }

    void updateBalance(GuestSession session, BigDecimal newBalance) {
        session.setBalance(newBalance);
    }

    /** 128 bits of entropy, URL-safe: guest ids are bearer references and must not be guessable. */
    private String newSessionId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Sweeps idle sessions. Runs on a timer so memory is reclaimed even with no traffic. */
    @Scheduled(fixedDelayString = "PT5M")
    public void evictExpired() {
        var ttl = properties.guest().sessionTtl();
        int before = sessions.size();
        sessions.values().removeIf(session -> session.isExpired(ttl));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.debug("Evicted {} expired guest session(s); {} remain", removed, sessions.size());
        }
    }

    public int activeSessionCount() {
        return sessions.size();
    }
}
