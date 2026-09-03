package com.casino.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration, bound from {@code application.yml} and overridable by environment
 * variables. No secret has a usable default baked into the source.
 */
@ConfigurationProperties(prefix = "casino")
public record CasinoProperties(Jwt jwt, Guest guest, Limits limits, Slots slots, Security security) {

    /**
     * @param secret     HMAC-SHA256 signing key, at least 32 bytes. Supplied via
     *                   {@code CASINO_JWT_SECRET}. When blank outside the dev profile the
     *                   application refuses to start.
     * @param ttl        how long an access token stays valid
     * @param issuer     the {@code iss} claim
     */
    public record Jwt(String secret, Duration ttl, String issuer) {
    }

    /**
     * @param sessionTtl how long an idle guest session survives before it is discarded
     * @param maxSessions ceiling on concurrent guest sessions, so the in-memory store cannot be
     *                    grown without bound by an attacker minting guest tokens
     */
    public record Guest(Duration sessionTtl, int maxSessions) {
    }

    /**
     * @param minBet              smallest accepted wager, unless an admin has changed it
     * @param maxBet              largest accepted wager per hand or spin, unless an admin has
     *                            changed it
     * @param maxConfigurableBet  hard ceiling on the maximum bet an admin may set; deliberately
     *                            not adjustable through the admin console
     * @param maxRouletteBets     guard on the size of a spin request, not a table rule: a
     *                            player is limited by their balance, not by a count of bets
     * @param maxBlackjackHands   most boxes one player may take in a single blackjack round
     * @param maxAdminCredit      ceiling on a single administrative credit
     */
    public record Limits(String minBet, String maxBet, String maxConfigurableBet,
                         int maxRouletteBets, int maxBlackjackHands, String maxAdminCredit) {
    }

    /**
     * Slot machine settings.
     *
     * <p>A slot machine is not a table game and is deliberately not covered by the admin-managed
     * table limits: there is no minimum, because a machine takes whatever denomination the
     * player dials in. The maximum here is a guard on the total a single spin may commit, and it
     * stays in configuration rather than in the admin console.
     *
     * @param creditOptions the fixed credit buttons on the cabinet; each credit lights one more
     *                      payline, so no option may exceed the number of paylines
     * @param maxTotalBet   ceiling on bet-per-line times credits for one spin
     */
    public record Slots(java.util.List<Integer> creditOptions, String maxTotalBet) {
    }

    /**
     * @param maxFailedLogins failed attempts before an account is temporarily locked
     * @param lockDuration    how long that lock lasts
     * @param authRatePerMinute auth requests allowed per client address per minute
     * @param allowedOrigins  browser origins permitted by CORS
     */
    public record Security(int maxFailedLogins, Duration lockDuration, int authRatePerMinute,
                           java.util.List<String> allowedOrigins) {
    }
}
