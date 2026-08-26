package com.casino.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An append-only record of every movement of money on a persisted account.
 *
 * <p>Rows are never updated or deleted. The balance on {@link UserAccount} is a running total
 * that must always equal the sum of the entries here, which makes tampering detectable and gives
 * players a dispute trail. {@code balanceAfter} is denormalised deliberately so that a
 * reconciliation job can spot a break without replaying the whole history.
 */
@Entity
@Table(name = "ledger_entry", indexes = {
        @Index(name = "idx_ledger_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_ledger_round", columnList = "round_id")
})
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 24)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, updatable = false, length = 16)
    private GameType game;

    /** Signed: negative for a bet, positive for a payout or credit. */
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /** Correlates the bet and the payout for a single round. */
    @Column(name = "round_id", updatable = false, length = 36)
    private String roundId;

    @Column(name = "detail", updatable = false, length = 512)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // for JPA
    }

    public LedgerEntry(Long userId, LedgerEntryType entryType, GameType game, BigDecimal amount,
                       BigDecimal balanceAfter, String roundId, String detail) {
        this.userId = userId;
        this.entryType = entryType;
        this.game = game;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.roundId = roundId;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public GameType getGame() {
        return game;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getRoundId() {
        return roundId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
