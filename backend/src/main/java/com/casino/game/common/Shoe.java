package com.casino.game.common;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-deck shoe dealt from the top, as in a real pit.
 *
 * <p>The shoe is built from {@code deckCount} standard 52-card decks and shuffled with an
 * unbiased Fisher-Yates pass driven by the injected {@link RandomSource}. A cut card sits at
 * {@code penetration} of the way through the shoe; once it is passed the shoe reshuffles at the
 * start of the next round rather than mid-hand, which is how a live game behaves and which
 * keeps card counting from being trivially defeated by a per-hand reshuffle.
 *
 * <p>Not thread-safe: one shoe belongs to one table/session.
 */
public final class Shoe implements CardSource {

    private final int deckCount;
    private final double penetration;
    private final RandomSource random;

    private final List<Card> cards = new ArrayList<>();
    private int position;

    public Shoe(int deckCount, double penetration, RandomSource random) {
        if (deckCount < 1) {
            throw new IllegalArgumentException("deckCount must be >= 1");
        }
        if (penetration <= 0.0 || penetration >= 1.0) {
            throw new IllegalArgumentException("penetration must be in (0,1)");
        }
        this.deckCount = deckCount;
        this.penetration = penetration;
        this.random = random;
        shuffle();
    }

    /** Rebuilds all {@code deckCount} decks and shuffles them, resetting the deal position. */
    public void shuffle() {
        cards.clear();
        for (int deck = 0; deck < deckCount; deck++) {
            for (Suit suit : Suit.values()) {
                for (Rank rank : Rank.values()) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        // Fisher-Yates: every permutation equally likely.
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Card tmp = cards.get(i);
            cards.set(i, cards.get(j));
            cards.set(j, tmp);
        }
        position = 0;
    }

    /** Deals the next card, auto-reshuffling if the shoe is somehow exhausted. */
    @Override
    public Card deal() {
        if (position >= cards.size()) {
            shuffle();
        }
        return cards.get(position++);
    }

    /** True once the cut card has been passed; the table reshuffles before the next round. */
    public boolean needsShuffle() {
        return position >= (int) Math.floor(cards.size() * penetration);
    }

    /** Reshuffles only if the cut card was reached. Call between rounds, never mid-hand. */
    @Override
    public void shuffleIfNeeded() {
        if (needsShuffle()) {
            shuffle();
        }
    }

    @Override
    public int cardsRemaining() {
        return cards.size() - position;
    }

    public int totalCards() {
        return cards.size();
    }

    public int deckCount() {
        return deckCount;
    }
}
