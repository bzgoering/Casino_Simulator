package com.casino.game.roulette;

/**
 * The bets available on an American layout, with the payout each returns on top of the stake.
 *
 * <p>These odds are the same ones a European table pays. That is the whole point of a
 * double-zero wheel: the payouts stay priced against 36 numbers while the wheel holds 38
 * pockets, so the extra green is taken straight out of the player's return. The edge is
 * 2/38 = 5.26% rather than 1/37 = 2.70%, and no number on this enum had to move for that.
 *
 * <p>{@link #TOP_LINE} is the exception, and the only bet an American cloth adds. Five pockets
 * paying 6:1 comes out at 7.89%, half again worse than everything else here. It is a famously
 * bad bet, and it is priced exactly as a real table prices it.
 *
 * <p>{@code selectionSize} is the exact number of pockets an inside bet must name, and is
 * enforced when the bet is built.
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
    /** The five-number bet, 0-00-1-2-3, 6:1. American cloths only, and the worst bet on one. */
    TOP_LINE(6, 5),
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
        return this == STRAIGHT || this == SPLIT || this == STREET || this == CORNER
                || this == SIX_LINE || this == TOP_LINE;
    }
}
