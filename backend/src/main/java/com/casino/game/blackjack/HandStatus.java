package com.casino.game.blackjack;

public enum HandStatus {
    /** Awaiting a decision from the player. */
    ACTIVE,
    /** Player stood, or was auto-stood after a double or a split ace. */
    STAND,
    /** Over 21. */
    BUST,
    /** Natural 21 on the first two cards, not counting 21 made after a split. */
    BLACKJACK
}
