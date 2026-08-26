package com.casino.game.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for currency amounts.
 *
 * <p>Every balance, bet and payout in this system is a {@link BigDecimal} scaled to 2 decimal
 * places. Binary floating point is never used for money: {@code 0.1 + 0.2 != 0.3} in a double,
 * and a casino ledger that drifts by a cent per round is a real bug.
 */
public final class Money {

    public static final BigDecimal ZERO = scaled(BigDecimal.ZERO);

    private Money() {
    }

    /** Normalises to 2dp. HALF_UP matches how a cashier rounds in the player's favour on a tie. */
    public static BigDecimal scaled(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String amount) {
        return scaled(new BigDecimal(amount));
    }

    public static BigDecimal of(long amount) {
        return scaled(BigDecimal.valueOf(amount));
    }

    /** Multiplies a stake by a payout multiplier and re-scales, e.g. 3:2 blackjack at 1.5. */
    public static BigDecimal multiply(BigDecimal amount, double multiplier) {
        return scaled(amount.multiply(BigDecimal.valueOf(multiplier)));
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
