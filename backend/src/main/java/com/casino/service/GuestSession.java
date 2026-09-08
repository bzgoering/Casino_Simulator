package com.casino.service;

import com.casino.game.common.Money;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transient state for one anonymous guest.
 *
 * <p>Guests are required to leave no data behind, so this object lives only in memory and is
 * dropped when it expires. It is the authoritative record of a guest balance while it exists:
 * the balance is never round-tripped through the browser, because anything the client holds it
 * could also edit or replay.
 */
public final class GuestSession {

    private final String id;
    private final Instant createdAt;
    private volatile BigDecimal balance;
    private volatile Instant lastAccessAt;

    GuestSession(String id, BigDecimal startingBalance) {
        this.id = id;
        this.balance = Money.scaled(startingBalance);
        this.createdAt = Instant.now();
        this.lastAccessAt = this.createdAt;
    }

    public String id() {
        return id;
    }

    public BigDecimal balance() {
        return balance;
    }

    /**
     * Takes a stake, or reports that the balance will not cover it.
     *
     * <p>Synchronised, and deliberately one operation rather than a read followed by a write.
     * {@code volatile} on the field gives visibility but not atomicity: two concurrent bets that
     * each read the balance, each find it sufficient and each write their own result would let a
     * guest stake the same money twice. A guest has no database row behind them, so there is no
     * row lock and no {@code CHECK (balance >= 0)} to catch that afterwards -- this method is the
     * only thing standing in the way, and so it has to be the whole check-and-subtract.
     *
     * @return {@code true} if the stake was taken, {@code false} if the balance is short
     */
    synchronized boolean tryDebit(BigDecimal stake) {
        BigDecimal amount = Money.scaled(stake);
        if (balance.compareTo(amount) < 0) {
            return false;
        }
        this.balance = Money.scaled(balance.subtract(amount));
        touch();
        return true;
    }

    /** Adds to the balance atomically, for the same reason {@link #tryDebit} is atomic. */
    synchronized BigDecimal creditBy(BigDecimal amount) {
        this.balance = Money.scaled(balance.add(Money.scaled(amount)));
        touch();
        return balance;
    }

    void touch() {
        this.lastAccessAt = Instant.now();
    }

    Instant lastAccessAt() {
        return lastAccessAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    boolean isExpired(java.time.Duration ttl) {
        return lastAccessAt.plus(ttl).isBefore(Instant.now());
    }
}
