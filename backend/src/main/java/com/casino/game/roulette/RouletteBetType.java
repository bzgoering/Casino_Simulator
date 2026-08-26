package com.casino.game.roulette;

/**
 * The bets available on a European layout, with the payout each returns on top of the stake.
 *
 * <p>Every payout here is priced against 36 numbers while the wheel has 37 pockets, giving the
 * uniform 2.70% house edge. {@code selectionSize} is the exact number of pockets an inside bet
 * must name, and is enforced when the bet is built.
 */
public enum RouletteBetType {

    /** One number, 35:1. */
    STRAIGHT(35, 1),
    /** Two adjacent numbers on the layout, 17:1. */
    SPLIT(17, 2),
    /** A row of three, 11:1. */
    STREET(11, 3),
    /** A square of four, 8:1. */
    CORNER(8, 4),
    /** Two adjacent streets, 5:1. */
    SIX_LINE(5, 6),
    /** One of the three columns of twelve, 2:1. */
    COLUMN(2, 12),
    /** 1-12, 13-24 or 25-36, 2:1. */
    DOZEN(2, 12),
    /** Red or black, 1:1. */
    COLOR(1, 18),
    /** Odd or even, 1:1. */
    PARITY(1, 18),
    /** 1-18 or 19-36, 1:1. */
    HALF(1, 18);

    private final int payoutToOne;
    private final int selectionSize;

    RouletteBetType(int payoutToOne, int selectionSize) {
        this.payoutToOne = payoutToOne;
        this.selectionSize = selectionSize;
    }

    /** Winnings per unit staked, excluding the returned stake. A straight-up pays 35 plus the 1 back. */
    public int payoutToOne() {
        return payoutToOne;
    }

    public int selectionSize() {
        return selectionSize;
    }

    /** Inside bets name their pockets explicitly; outside bets are named by a keyword instead. */
    public boolean isInsideBet() {
        return this == STRAIGHT || this == SPLIT || this == STREET || this == CORNER || this == SIX_LINE;
    }
}
