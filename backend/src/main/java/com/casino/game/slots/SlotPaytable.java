package com.casino.game.slots;

import java.util.List;

/**
 * Paytable and reel strip for the house three-reel machine.
 *
 * <p>Each reel carries the same 32-stop strip, so the outcome space is exactly 32^3 = 32,768
 * equally likely combinations. That makes the return to player (RTP) exactly computable rather
 * than a guess, and {@code SlotPaytableTest} enumerates the whole space to assert it.
 *
 * <p>The machine shows three rows and pays on five lines, but that does not move the RTP. Every
 * line reads one symbol per reel, each a uniform draw over the same strip and independent across
 * reels, so each line has exactly the distribution the old single-line machine had. Expectation
 * adds, so the return is the same whether the player lights one line or five: more lines buy
 * more chances at the same price per chance, not a better or worse machine.
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

    /** Rows visible on each reel: the stop itself plus one either side of it. */
    public static final int ROW_COUNT = 3;

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

    /** Scores three symbols given as a line, in reel order. */
    public static int multiplierFor(List<SlotSymbol> line) {
        return multiplierFor(line.get(0), line.get(1), line.get(2));
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

    public static String describe(List<SlotSymbol> line) {
        return describe(line.get(0), line.get(1), line.get(2));
    }

    /**
     * The three symbols visible on one reel when it stops at {@code stop}: the stop itself on the
     * centre row, with its neighbours above and below.
     *
     * <p>The strip is a physical loop, so the window wraps at the ends rather than running out.
     */
    public static List<SlotSymbol> windowAt(int stop) {
        int size = REEL_STRIP.size();
        return List.of(
                REEL_STRIP.get(Math.floorMod(stop - 1, size)),
                REEL_STRIP.get(Math.floorMod(stop, size)),
                REEL_STRIP.get(Math.floorMod(stop + 1, size)));
    }
}
