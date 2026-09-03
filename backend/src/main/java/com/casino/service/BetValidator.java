package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.GameLimits;
import com.casino.domain.GameType;
import com.casino.game.common.Money;
import com.casino.repository.GameLimitsRepository;
import com.casino.web.error.CasinoException;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the betting limits and enforces them on every wager.
 *
 * <p>Runs server-side and unconditionally. The browser shows the same limits, but that is a
 * convenience for the player, not a control: the only check that counts is this one.
 *
 * <p>Limits are per game, adjustable by an administrator, held in a concurrent map and mirrored
 * to a database row per game. A stored row wins at startup; otherwise the values from
 * {@code application.yml} apply, which keeps a fresh database working with no seed step. A hard
 * ceiling stays in configuration, so nothing reachable through the admin console can raise a
 * maximum bet without a deploy.
 */
@Component
public class BetValidator {

    private static final Logger log = LoggerFactory.getLogger(BetValidator.class);

    /**
     * The table games, whose limits an administrator sets.
     *
     * <p>Slots are absent on purpose. A machine is not a table game: it has no house minimum and
     * its own per-spin ceiling lives in configuration, so putting it in the admin console would
     * offer a control that does not apply to it.
     */
    public static final List<GameType> TABLE_GAMES =
            List.of(GameType.BLACKJACK, GameType.ROULETTE);

    private final GameLimitsRepository store;
    private final int maxRouletteBets;
    private final BigDecimal ceiling;
    private final Limits configured;

    private final Map<GameType, Limits> limits = new ConcurrentHashMap<>();

    public BetValidator(CasinoProperties properties, GameLimitsRepository store) {
        this.store = store;
        this.maxRouletteBets = properties.limits().maxRouletteBets();
        this.ceiling = Money.of(properties.limits().maxConfigurableBet());
        this.configured = new Limits(
                Money.of(properties.limits().minBet()), Money.of(properties.limits().maxBet()));
        for (GameType game : TABLE_GAMES) {
            limits.put(game, configured);
        }
    }

    /** Adopts any limits an administrator set in an earlier run. */
    @PostConstruct
    void loadStoredLimits() {
        try {
            for (GameLimits stored : store.findAll()) {
                if (stored.getGame() != null && limits.containsKey(stored.getGame())) {
                    limits.put(stored.getGame(), new Limits(stored.getMinBet(), stored.getMaxBet()));
                    log.info("{} limits loaded from the database: {} to {}",
                            stored.getGame(), stored.getMinBet(), stored.getMaxBet());
                }
            }
        } catch (RuntimeException e) {
            // Better to open the tables on the configured limits than not to open at all.
            log.warn("Could not read stored game limits; using the configured {} to {}",
                    configured.min(), configured.max(), e);
        }
    }

    /**
     * Replaces one game's limits and persists them.
     *
     * @param actor the admin username, recorded on the row
     * @throws CasinoException with 400 when the pair is not a usable range
     */
    @Transactional
    public Limits updateLimits(GameType game, BigDecimal requestedMin, BigDecimal requestedMax,
                               String actor) {
        GameType target = requireTableGame(game);
        BigDecimal min = requireAmount(requestedMin, "minimum");
        BigDecimal max = requireAmount(requestedMax, "maximum");

        if (max.compareTo(min) < 0) {
            throw CasinoException.badRequest("Maximum below minimum.");
        }
        if (max.compareTo(ceiling) > 0) {
            throw CasinoException.badRequest("Maximum above " + ceiling + ".");
        }

        GameLimits row = store.findById(target).orElse(null);
        if (row == null) {
            row = new GameLimits(target, min, max, actor);
        } else {
            row.apply(min, max, actor);
        }
        store.save(row);

        Limits updated = new Limits(min, max);
        limits.put(target, updated);
        log.info("Admin {} set {} limits to {} - {}", actor, target, min, max);
        return updated;
    }

    private static GameType requireTableGame(GameType game) {
        if (game == null || !TABLE_GAMES.contains(game)) {
            throw CasinoException.badRequest("Not a table game.");
        }
        return game;
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

    /** Validates a stake against one game's limits and returns it normalised to 2dp. */
    public BigDecimal validate(GameType game, BigDecimal amount) {
        if (amount == null) {
            throw CasinoException.badRequest("Bet amount required.");
        }
        // Reject extra precision rather than rounding it away: a request for 1.005 is a client
        // bug or a probe, and silently accepting it invites rounding games.
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Two decimal places max.");
        }
        BigDecimal stake = Money.scaled(amount);
        Limits bounds = limitsFor(game);
        if (stake.compareTo(bounds.min()) < 0) {
            throw CasinoException.badRequest("Below " + bounds.min() + " minimum.");
        }
        if (stake.compareTo(bounds.max()) > 0) {
            throw CasinoException.badRequest("Above " + bounds.max() + " maximum.");
        }
        return stake;
    }

    /**
     * Roulette puts no rule on how many spaces a player may cover: what they can afford is the
     * limit, and the wallet enforces that when the stake is debited.
     *
     * <p>The ceiling that remains is a guard on the request, not a rule of the table. Every
     * distinct bet an American cloth can print comes to well under two hundred, so no amount of
     * clicking reaches this; it is here so an unbounded array cannot be posted at the server.
     */
    public void validateRouletteBetCount(int count) {
        if (count < 1) {
            throw CasinoException.badRequest("No bets placed.");
        }
        if (count > maxRouletteBets) {
            throw CasinoException.badRequest("That is more bets than a cloth can hold.");
        }
    }

    /** The limits in force for one game. */
    public Limits limitsFor(GameType game) {
        return limits.getOrDefault(game, configured);
    }

    /** Every table game's limits, in a stable order, for the config and admin screens. */
    public Map<GameType, Limits> all() {
        Map<GameType, Limits> snapshot = new EnumMap<>(GameType.class);
        for (GameType game : TABLE_GAMES) {
            snapshot.put(game, limitsFor(game));
        }
        return snapshot;
    }

    /** The highest maximum bet an administrator is allowed to set, on any game. */
    public BigDecimal maxConfigurableBet() {
        return ceiling;
    }

    public int maxRouletteBets() {
        return maxRouletteBets;
    }

    /** One game's accepted wager range. */
    public record Limits(BigDecimal min, BigDecimal max) {
    }
}
