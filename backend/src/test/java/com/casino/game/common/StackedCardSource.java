package com.casino.game.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A card source that deals a scripted sequence, for tests that need a specific hand.
 *
 * <p>Cards are given in dealing order. Blackjack deals player, dealer-up, player, dealer-hole,
 * then draws in turn, so a test writes the sequence exactly as the table would see it.
 */
public final class StackedCardSource implements CardSource {

    private final Deque<Card> cards = new ArrayDeque<>();
    private int shuffleRequests;

    public StackedCardSource(List<Card> cards) {
        this.cards.addAll(cards);
    }

    /** Builds from short codes in dealing order, e.g. {@code of("AS", "10H", "KD", "5C")}. */
    public static StackedCardSource of(String... codes) {
        return new StackedCardSource(java.util.Arrays.stream(codes)
                .map(StackedCardSource::parse)
                .toList());
    }

    private static Card parse(String code) {
        String rankPart = code.substring(0, code.length() - 1);
        String suitPart = code.substring(code.length() - 1);
        Rank rank = java.util.Arrays.stream(Rank.values())
                .filter(r -> r.symbol().equals(rankPart))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown rank in " + code));
        Suit suit = java.util.Arrays.stream(Suit.values())
                .filter(s -> s.symbol().equals(suitPart))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown suit in " + code));
        return new Card(rank, suit);
    }

    @Override
    public Card deal() {
        Card card = cards.poll();
        if (card == null) {
            throw new IllegalStateException("Stacked deck exhausted: the test needs more cards");
        }
        return card;
    }

    @Override
    public void shuffleIfNeeded() {
        shuffleRequests++;
    }

    @Override
    public int cardsRemaining() {
        return cards.size();
    }

    public int shuffleRequests() {
        return shuffleRequests;
    }
}
