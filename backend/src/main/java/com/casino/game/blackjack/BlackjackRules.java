package com.casino.game.blackjack;

/**
 * Table rules. Defaults model a standard 8-deck Vegas shoe game:
 * dealer stands on all 17s, blackjack pays 3:2, double after split allowed, up to 4 hands,
 * split aces receive exactly one card each.
 *
 * @param deckCount        decks in the shoe
 * @param penetration      fraction of the shoe dealt before the cut card triggers a reshuffle
 * @param dealerHitsSoft17 {@code true} for H17 tables (worse for the player), {@code false} for S17
 * @param blackjackPayout  multiplier on the stake for a natural; 1.5 is 3:2, 1.2 is the 6:5 tables
 * @param maxSplits        how many times a hand may be split (3 splits = 4 hands)
 * @param doubleAfterSplit whether doubling is allowed on a hand created by a split
 */
public record BlackjackRules(
        int deckCount,
        double penetration,
        boolean dealerHitsSoft17,
        double blackjackPayout,
        int maxSplits,
        boolean doubleAfterSplit) {

    public static BlackjackRules standard() {
        return new BlackjackRules(8, 0.75, false, 1.5, 3, true);
    }

    public BlackjackRules {
        if (deckCount < 1) {
            throw new IllegalArgumentException("deckCount must be >= 1");
        }
        if (maxSplits < 0) {
            throw new IllegalArgumentException("maxSplits must be >= 0");
        }
        if (blackjackPayout <= 0) {
            throw new IllegalArgumentException("blackjackPayout must be > 0");
        }
    }
}
