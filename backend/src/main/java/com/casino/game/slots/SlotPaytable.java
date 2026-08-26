package com.casino.game.slots;

import java.util.List;

/**
 * Paytable and reel strip for the house three-reel, single-payline machine.
 *
 * <p>Each reel carries the same 32-stop strip, so the outcome space is exactly 32^3 = 32,768
 * equally likely combinations. That makes the return to player (RTP) exactly computable rather
 * than a guess, and {@code SlotRtpTest} enumerates the whole space to assert it.
 *
 * <p>The strip weights and multipliers below are tuned to <strong>96.01% RTP</strong>
 * (a 3.99% house edge) with a 22.4% hit frequency, which is typical of a real land-based
 * three-reel machine.
 *
 * <p>Payouts are quoted as a multiplier on the line bet and are inclusive of the stake: a
 * multiplier of 2 on a 1.00 bet returns 2.00, a net profit of 1.00.
 */
public final class SlotPaytable {

    /**
     * One reel strip, 32 stops. Symbol frequency is what actually sets the odds; the
     * physical order only matters for how the reel is drawn in the browser.
     */
    public static final List<SlotSymbol> REEL_STRIP = List.of(
            SlotSymbol.SEVEN,
            SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY,
            SlotSymbol.CHERRY, SlotSymbol.CHERRY, SlotSymbol.CHERRY,
            SlotSymbol.BAR3, SlotSymbol.BAR3,
            SlotSymbol.ORANGE, SlotSymbol.ORANGE, SlotSymbol.ORANGE,
            SlotSymbol.ORANGE, SlotSymbol.ORANGE, SlotSymbol.ORANGE,
            SlotSymbol.BAR2, SlotSymbol.BAR2, SlotSymbol.BAR2,
            SlotSymbol.PLUM, SlotSymbol.PLUM, SlotSymbol.PLUM,
            SlotSymbol.PLUM, SlotSymbol.PLUM,
            SlotSymbol.BAR1, SlotSymbol.BAR1, SlotSymbol.BAR1, SlotSymbol.BAR1,
            SlotSymbol.BELL, SlotSymbol.BELL, SlotSymbol.BELL,
            SlotSymbol.BELL, SlotSymbol.BELL);

    public static final int REEL_COUNT = 3;

    private SlotPaytable() {
    }

    /**
     * Scores a spin and returns the payout multiplier on the line bet, or 0 for no win.
     * Rules are checked best-first and are mutually exclusive.
     */
    public static int multiplierFor(SlotSymbol first, SlotSymbol second, SlotSymbol third) {
        if (first == second && second == third) {
            return switch (first) {
                case SEVEN -> 200;
                case BAR3 -> 100;
                case BAR2 -> 50;
                case BAR1 -> 26;
                case BELL -> 20;
                case PLUM -> 15;
                case ORANGE -> 10;
                case CHERRY -> 10;
            };
        }
        // Any three bars of mixed denomination.
        if (first.isBar() && second.isBar() && third.isBar()) {
            return 5;
        }
        // Cherries pay from the left, even without a full line.
        if (first == SlotSymbol.CHERRY && second == SlotSymbol.CHERRY) {
            return 6;
        }
        if (first == SlotSymbol.CHERRY) {
            return 2;
        }
        return 0;
    }

    /** Human-readable name of the winning combination, for the UI and the round log. */
    public static String describe(SlotSymbol first, SlotSymbol second, SlotSymbol third) {
        if (first == second && second == third) {
            return "Three " + first.displayName() + "s";
        }
        if (first.isBar() && second.isBar() && third.isBar()) {
            return "Mixed Bars";
        }
        if (first == SlotSymbol.CHERRY && second == SlotSymbol.CHERRY) {
            return "Two Cherries";
        }
        if (first == SlotSymbol.CHERRY) {
            return "One Cherry";
        }
        return "No win";
    }
}
