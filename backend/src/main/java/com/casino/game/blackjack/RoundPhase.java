package com.casino.game.blackjack;

public enum RoundPhase {
    /** One or more player hands still need decisions. */
    PLAYER_TURN,
    /** All player hands resolved; dealer has drawn and the round is scored. */
    SETTLED
}
