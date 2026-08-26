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

    void setBalance(BigDecimal balance) {
        this.balance = Money.scaled(balance);
        touch();
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
