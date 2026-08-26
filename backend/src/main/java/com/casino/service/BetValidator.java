package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.game.common.Money;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Enforces table limits on every wager.
 *
 * <p>Runs server-side and unconditionally. The browser shows the same limits, but that is a
 * convenience for the player, not a control: the only check that counts is this one.
 */
@Component
public class BetValidator {

    private final BigDecimal minBet;
    private final BigDecimal maxBet;
    private final int maxRouletteBets;

    public BetValidator(CasinoProperties properties) {
        this.minBet = Money.of(properties.limits().minBet());
        this.maxBet = Money.of(properties.limits().maxBet());
        this.maxRouletteBets = properties.limits().maxRouletteBets();
    }

    /** Validates a single stake and returns it normalised to 2dp. */
    public BigDecimal validate(BigDecimal amount) {
        if (amount == null) {
            throw CasinoException.badRequest("A bet amount is required.");
        }
        // Reject extra precision rather than rounding it away: a request for 1.005 is a client
        // bug or a probe, and silently accepting it invites rounding games.
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Bets can have at most 2 decimal places.");
        }
        BigDecimal stake = Money.scaled(amount);
        if (stake.compareTo(minBet) < 0) {
            throw CasinoException.badRequest("Minimum bet is " + minBet + ".");
        }
        if (stake.compareTo(maxBet) > 0) {
            throw CasinoException.badRequest("Maximum bet is " + maxBet + ".");
        }
        return stake;
    }

    /** Roulette allows many chips on one spin; the count and the total are both capped. */
    public void validateRouletteBetCount(int count) {
        if (count < 1) {
            throw CasinoException.badRequest("Place at least one bet before spinning.");
        }
        if (count > maxRouletteBets) {
            throw CasinoException.badRequest("At most " + maxRouletteBets + " bets per spin.");
        }
    }

    public BigDecimal minBet() {
        return minBet;
    }

    public BigDecimal maxBet() {
        return maxBet;
    }

    public int maxRouletteBets() {
        return maxRouletteBets;
    }
}
