package com.casino.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The betting limits for one game, as an administrator last set them.
 *
 * <p>One row per game: blackjack, slots and roulette are different products, and a single pair
 * of bounds for the whole house forces the same floor on a slot spin as on a blackjack box.
 * Storing them makes a change survive a restart; keeping them in a table rather than in
 * configuration is what lets an admin change them without a redeploy.
 *
 * <p>A game with no row falls back to the values in {@code application.yml}.
 */
@Entity
@Table(name = "game_limits")
public class GameLimits {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, updatable = false, length = 16)
    private GameType game;

    @Column(name = "min_bet", nullable = false, precision = 19, scale = 2)
    private BigDecimal minBet;

    @Column(name = "max_bet", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxBet;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The admin who last changed them, kept alongside the fuller admin audit trail. */
    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    protected GameLimits() {
    }

    public GameLimits(GameType game, BigDecimal minBet, BigDecimal maxBet, String updatedBy) {
        this.game = game;
        apply(minBet, maxBet, updatedBy);
    }

    public void apply(BigDecimal minBet, BigDecimal maxBet, String updatedBy) {
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public GameType getGame() {
        return game;
    }

    public BigDecimal getMinBet() {
        return minBet;
    }

    public BigDecimal getMaxBet() {
        return maxBet;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
