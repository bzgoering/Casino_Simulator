package com.casino.game.blackjack;

public enum HandOutcome {
    /** Natural blackjack, paid at the table's blackjack multiplier. */
    BLACKJACK,
    /** Beat the dealer, paid 1:1. */
    WIN,
    /** Lost the stake. */
    LOSE,
    /** Tie, stake returned. */
    PUSH
}
