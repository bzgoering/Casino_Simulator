package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.TableLimits;
import com.casino.game.common.Money;
import com.casino.repository.TableLimitsRepository;
import com.casino.web.error.CasinoException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the table limits and enforces them on every wager.
 *
 * <p>Runs server-side and unconditionally. The browser shows the same limits, but that is a
 * convenience for the player, not a control: the only check that counts is this one.
 *
 * <p>The limits are adjustable by an administrator, so they are held in volatile fields and
 * mirrored to a database row. The row wins at startup when it exists; otherwise the values from
 * {@code application.yml} apply, which keeps a fresh database working with no seed step. A hard
 * ceiling stays in configuration, so nothing reachable through the admin console can raise the
 * maximum bet without a deploy.
 */
@Component
public class BetValidator {

    private static final Logger log = LoggerFactory.getLogger(BetValidator.class);

    private final TableLimitsRepository store;
    private final int maxRouletteBets;
    private final BigDecimal configuredMinBet;
    private final BigDecimal configuredMaxBet;
    private final BigDecimal ceiling;

    private volatile BigDecimal minBet;
    private volatile BigDecimal maxBet;

    public BetValidator(CasinoProperties properties, TableLimitsRepository store) {
        this.store = store;
        this.maxRouletteBets = properties.limits().maxRouletteBets();
        this.configuredMinBet = Money.of(properties.limits().minBet());
        this.configuredMaxBet = Money.of(properties.limits().maxBet());
        this.ceiling = Money.of(properties.limits().maxConfigurableBet());
        this.minBet = configuredMinBet;
        this.maxBet = configuredMaxBet;
    }

    /** Adopts any limits an administrator set in an earlier run. */
    @PostConstruct
    void loadStoredLimits() {
        try {
            store.findById(TableLimits.SINGLETON_ID).ifPresent(stored -> {
                minBet = stored.getMinBet();
                maxBet = stored.getMaxBet();
                log.info("Table limits loaded from the database: {} to {}", minBet, maxBet);
            });
        } catch (RuntimeException e) {
            // Better to open the tables on the configured limits than not to open at all.
            log.warn("Could not read stored table limits; using the configured {} to {}",
                    configuredMinBet, configuredMaxBet, e);
        }
    }

    /**
     * Replaces the limits and persists them.
     *
     * @param actor the admin username, recorded on the row
     * @throws CasinoException with 400 when the pair is not a usable range
     */
    @Transactional
    public void updateLimits(BigDecimal requestedMin, BigDecimal requestedMax, String actor) {
        BigDecimal min = requireAmount(requestedMin, "minimum");
        BigDecimal max = requireAmount(requestedMax, "maximum");

        if (max.compareTo(min) < 0) {
            throw CasinoException.badRequest("Maximum below minimum.");
        }
        if (max.compareTo(ceiling) > 0) {
            throw CasinoException.badRequest("Maximum above " + ceiling + ".");
        }

        TableLimits row = store.findById(TableLimits.SINGLETON_ID).orElse(null);
        if (row == null) {
            row = new TableLimits(min, max, actor);
        } else {
            row.apply(min, max, actor);
        }
        store.save(row);

        this.minBet = min;
        this.maxBet = max;
        log.info("Admin {} set table limits to {} - {}", actor, min, max);
    }

    private BigDecimal requireAmount(BigDecimal amount, String which) {
        if (amount == null) {
            throw CasinoException.badRequest("A " + which + " is required.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Two decimal places max.");
        }
        BigDecimal scaled = Money.scaled(amount);
        if (!Money.isPositive(scaled)) {
            throw CasinoException.badRequest("Limits must be positive.");
        }
        return scaled;
    }

    /** Validates a single stake and returns it normalised to 2dp. */
    public BigDecimal validate(BigDecimal amount) {
        if (amount == null) {
            throw CasinoException.badRequest("Bet amount required.");
        }
        // Reject extra precision rather than rounding it away: a request for 1.005 is a client
        // bug or a probe, and silently accepting it invites rounding games.
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Two decimal places max.");
        }
        BigDecimal stake = Money.scaled(amount);
        BigDecimal min = minBet;
        BigDecimal max = maxBet;
        if (stake.compareTo(min) < 0) {
            throw CasinoException.badRequest("Below " + min + " minimum.");
        }
        if (stake.compareTo(max) > 0) {
            throw CasinoException.badRequest("Above " + max + " maximum.");
        }
        return stake;
    }

    /** Roulette allows many chips on one spin; the count and the total are both capped. */
    public void validateRouletteBetCount(int count) {
        if (count < 1) {
            throw CasinoException.badRequest("No bets placed.");
        }
        if (count > maxRouletteBets) {
            throw CasinoException.badRequest("At most " + maxRouletteBets + " bets.");
        }
    }

    public BigDecimal minBet() {
        return minBet;
    }

    public BigDecimal maxBet() {
        return maxBet;
    }

    /** The highest maximum bet an administrator is allowed to set. */
    public BigDecimal maxConfigurableBet() {
        return ceiling;
    }

    public int maxRouletteBets() {
        return maxRouletteBets;
    }
}
