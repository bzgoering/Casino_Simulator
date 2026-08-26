package com.casino.game.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShoeTest {

    @Test
    @DisplayName("an 8-deck shoe holds 416 cards")
    void eightDeckShoeHasFourHundredAndSixteenCards() {
        Shoe shoe = new Shoe(8, 0.75, new SeededRandomSource(1L));

        assertThat(shoe.totalCards()).isEqualTo(8 * 52);
        assertThat(shoe.cardsRemaining()).isEqualTo(416);
        assertThat(shoe.deckCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("shuffling preserves deck composition: 32 of each rank across 8 decks")
    void shufflePreservesComposition() {
        Shoe shoe = new Shoe(8, 0.75, new SeededRandomSource(42L));

        Map<Rank, Integer> rankCounts = new HashMap<>();
        Map<Suit, Integer> suitCounts = new HashMap<>();
        for (int i = 0; i < 416; i++) {
            Card card = shoe.deal();
            rankCounts.merge(card.rank(), 1, Integer::sum);
            suitCounts.merge(card.suit(), 1, Integer::sum);
        }

        // 8 decks x 4 suits = 32 of every rank; 8 decks x 13 ranks = 104 of every suit.
        assertThat(rankCounts).hasSize(13).allSatisfy((rank, count) -> assertThat(count).isEqualTo(32));
        assertThat(suitCounts).hasSize(4).allSatisfy((suit, count) -> assertThat(count).isEqualTo(104));
    }

    @Test
    @DisplayName("the cut card triggers a reshuffle only once penetration is reached")
    void cutCardTriggersReshuffle() {
        Shoe shoe = new Shoe(8, 0.75, new SeededRandomSource(7L));
        int cutCardAt = (int) Math.floor(416 * 0.75);

        for (int i = 0; i < cutCardAt - 1; i++) {
            shoe.deal();
        }
        assertThat(shoe.needsShuffle()).isFalse();

        shoe.deal();
        assertThat(shoe.needsShuffle()).isTrue();

        shoe.shuffleIfNeeded();
        assertThat(shoe.cardsRemaining()).isEqualTo(416);
        assertThat(shoe.needsShuffle()).isFalse();
    }

    @Test
    @DisplayName("shuffleIfNeeded is a no-op before the cut card, so a shoe is not reset mid-game")
    void shuffleIfNeededDoesNothingEarly() {
        Shoe shoe = new Shoe(8, 0.75, new SeededRandomSource(3L));
        shoe.deal();
        shoe.deal();

        shoe.shuffleIfNeeded();

        assertThat(shoe.cardsRemaining()).isEqualTo(414);
    }

    @Test
    @DisplayName("two different seeds produce different orderings")
    void differentSeedsShuffleDifferently() {
        Shoe first = new Shoe(8, 0.75, new SeededRandomSource(1L));
        Shoe second = new Shoe(8, 0.75, new SeededRandomSource(2L));

        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            a.append(first.deal().code());
            b.append(second.deal().code());
        }

        assertThat(a.toString()).isNotEqualTo(b.toString());
    }

    @Test
    @DisplayName("invalid construction is rejected")
    void rejectsInvalidConfiguration() {
        RandomSource random = new SeededRandomSource(1L);

        assertThatThrownBy(() -> new Shoe(0, 0.75, random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deckCount");
        assertThatThrownBy(() -> new Shoe(8, 0.0, random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("penetration");
        assertThatThrownBy(() -> new Shoe(8, 1.0, random))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("penetration");
    }
}
