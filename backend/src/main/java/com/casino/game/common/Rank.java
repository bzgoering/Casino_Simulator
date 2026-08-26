package com.casino.game.common;

public enum Rank {
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 10),
    QUEEN("Q", 10),
    KING("K", 10),
    /** Aces are carried as 11 and demoted to 1 by the hand evaluator when needed. */
    ACE("A", 11);

    private final String symbol;
    private final int blackjackValue;

    Rank(String symbol, int blackjackValue) {
        this.symbol = symbol;
        this.blackjackValue = blackjackValue;
    }

    public String symbol() {
        return symbol;
    }

    public int blackjackValue() {
        return blackjackValue;
    }
}
