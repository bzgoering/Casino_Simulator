package com.casino.game.common;

/**
 * Where a game gets its cards.
 *
 * <p>Production always uses a real {@link Shoe}. The interface exists so a game can be driven by
 * a known sequence of cards in a test: asserting that a natural blackjack pays 3:2 requires
 * actually dealing one, which is not something a shuffled shoe can be asked to do.
 */
public interface CardSource {

    /** The next card off the top. */
    Card deal();

    /** Reshuffles if the cut card has been reached. Called between rounds, never mid-hand. */
    void shuffleIfNeeded();

    int cardsRemaining();
}
