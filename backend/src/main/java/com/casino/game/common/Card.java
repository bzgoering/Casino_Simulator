package com.casino.game.common;

/** An immutable playing card. {@link #code()} is the wire format sent to the browser. */
public record Card(Rank rank, Suit suit) {

    /** e.g. {@code "AS"} for the ace of spades, {@code "10H"} for the ten of hearts. */
    public String code() {
        return rank.symbol() + suit.symbol();
    }

    @Override
    public String toString() {
        return code();
    }
}
