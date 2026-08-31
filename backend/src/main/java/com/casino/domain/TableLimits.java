package com.casino.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The house betting limits, as an administrator last set them.
 *
 * <p>Exactly one row, keyed by a fixed id. Storing them makes a limit change survive a restart;
 * keeping them in a table rather than in configuration is what lets an admin change them without
 * a redeploy. The row only exists once someone has changed the limits: until then the values in
 * {@code application.yml} apply.
 */
@Entity
@Table(name = "table_limits")
public class TableLimits {

    /** The one and only row. */
    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "min_bet", nullable = false, precision = 19, scale = 2)
    private BigDecimal minBet;

    @Column(name = "max_bet", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxBet;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The admin who last changed the limits, kept alongside the fuller admin audit trail. */
    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    protected TableLimits() {
    }

    public TableLimits(BigDecimal minBet, BigDecimal maxBet, String updatedBy) {
        this.id = SINGLETON_ID;
        apply(minBet, maxBet, updatedBy);
    }

    public void apply(BigDecimal minBet, BigDecimal maxBet, String updatedBy) {
        this.minBet = minBet;
        this.maxBet = maxBet;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
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
