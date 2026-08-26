package com.casino.game.blackjack;

import static org.assertj.core.api.Assertions.assertThat;

import com.casino.game.common.Card;
import com.casino.game.common.Rank;
import com.casino.game.common.Suit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HandValueTest {

    private static Card card(Rank rank) {
        return new Card(rank, Suit.SPADES);
    }

    @Test
    @DisplayName("an ace counts as 11 while that does not bust")
    void aceCountsHigh() {
        HandValue value = HandValue.evaluate(List.of(card(Rank.ACE), card(Rank.SIX)));

        assertThat(value.total()).isEqualTo(17);
        assertThat(value.soft()).isTrue();
    }

    @Test
    @DisplayName("an ace demotes to 1 rather than busting the hand")
    void aceDemotesToAvoidBust() {
        HandValue value = HandValue.evaluate(
                List.of(card(Rank.ACE), card(Rank.SIX), card(Rank.KING)));

        assertThat(value.total()).isEqualTo(17);
        assertThat(value.soft()).isFalse();
        assertThat(value.isBust()).isFalse();
    }

    @Test
    @DisplayName("several aces demote one at a time")
    void multipleAcesDemoteIndividually() {
        // A + A = 12 (one high, one low), not 22 and not 2.
        assertThat(HandValue.evaluate(List.of(card(Rank.ACE), card(Rank.ACE))).total()).isEqualTo(12);

        // A + A + 9 = 21: one ace stays high.
        HandValue three = HandValue.evaluate(List.of(card(Rank.ACE), card(Rank.ACE), card(Rank.NINE)));
        assertThat(three.total()).isEqualTo(21);
        assertThat(three.soft()).isTrue();

        // Four aces = 14, exactly one still counted as 11.
        HandValue four = HandValue.evaluate(
                List.of(card(Rank.ACE), card(Rank.ACE), card(Rank.ACE), card(Rank.ACE)));
        assertThat(four.total()).isEqualTo(14);
        assertThat(four.soft()).isTrue();
    }

    @Test
    @DisplayName("a hand with no usable ace can bust")
    void handCanBust() {
        HandValue value = HandValue.evaluate(
                List.of(card(Rank.KING), card(Rank.QUEEN), card(Rank.FIVE)));

        assertThat(value.total()).isEqualTo(25);
        assertThat(value.isBust()).isTrue();
        assertThat(value.soft()).isFalse();
    }

    @Test
    @DisplayName("all face cards are worth ten")
    void faceCardsAreTen() {
        for (Rank rank : List.of(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)) {
            assertThat(HandValue.evaluate(List.of(card(rank), card(Rank.FIVE))).total()).isEqualTo(15);
        }
    }
}
