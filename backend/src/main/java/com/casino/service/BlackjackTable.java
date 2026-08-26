package com.casino.service;

import com.casino.game.blackjack.BlackjackRound;
import com.casino.game.common.Shoe;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One player's seat: their shoe, their current round, and a lock.
 *
 * <p>The shoe lives across rounds and only reshuffles when the cut card is reached, exactly as a
 * real one does. Rebuilding it every hand would be simpler but would quietly change the game.
 *
 * <p>The lock serialises actions on this seat. Two requests arriving together, say a double-click
 * on "hit", must not both mutate the same round.
 */
final class BlackjackTable {

    private final Shoe shoe;
    private final ReentrantLock lock = new ReentrantLock();
    private BlackjackRound round;
    private String roundId;
    private volatile Instant lastAccessAt = Instant.now();

    BlackjackTable(Shoe shoe) {
        this.shoe = shoe;
    }

    Shoe shoe() {
        return shoe;
    }

    BlackjackRound round() {
        return round;
    }

    void setRound(BlackjackRound round) {
        this.round = round;
    }

    String roundId() {
        return roundId;
    }

    void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    ReentrantLock lock() {
        return lock;
    }

    void touch() {
        lastAccessAt = Instant.now();
    }

    boolean isExpired(Duration ttl) {
        return lastAccessAt.plus(ttl).isBefore(Instant.now());
    }
}
