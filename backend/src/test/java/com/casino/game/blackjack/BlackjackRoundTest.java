package com.casino.game.blackjack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casino.game.common.Money;
import com.casino.game.common.StackedCardSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Rules tests driven by a scripted deck.
 *
 * <p>Cards are listed in real dealing order: player, dealer upcard, player, dealer hole card,
 * then whatever is drawn afterwards.
 */
class BlackjackRoundTest {

    private static final BigDecimal BET = Money.of("10.00");
    private static final BigDecimal PLENTY = Money.of("1000.00");
    private static final BlackjackRules STANDARD = BlackjackRules.standard();

    private static BlackjackRound round(String... cards) {
        return new BlackjackRound(STANDARD, StackedCardSource.of(cards), BET);
    }

    @Nested
    @DisplayName("naturals")
    class Naturals {

        @Test
        @DisplayName("a natural blackjack pays 3:2 and ends the round immediately")
        void naturalPaysThreeToTwo() {
            BlackjackRound round = round("AS", "9D", "KH", "7C");

            assertThat(round.isSettled()).isTrue();
            assertThat(round.hands().get(0).status()).isEqualTo(HandStatus.BLACKJACK);
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.BLACKJACK);
            // 10.00 stake back plus 15.00 winnings.
            assertThat(round.totalPayout()).isEqualByComparingTo("25.00");
            assertThat(round.netResult()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("blackjack against a dealer blackjack is a push")
        void bothNaturalsPush() {
            BlackjackRound round = round("AS", "AD", "KH", "KC");

            assertThat(round.isSettled()).isTrue();
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.PUSH);
            assertThat(round.totalPayout()).isEqualByComparingTo("10.00");
            assertThat(round.netResult()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("the dealer peeks on an ace and takes the bet at once")
        void dealerNaturalEndsRound() {
            BlackjackRound round = round("10S", "AD", "7H", "KC");

            assertThat(round.isSettled()).isTrue();
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.LOSE);
            assertThat(round.totalPayout()).isEqualByComparingTo("0.00");
            assertThat(round.netResult()).isEqualByComparingTo("-10.00");
        }

        @Test
        @DisplayName("the dealer does not peek behind a low upcard, so play continues")
        void noPeekOnLowUpcard() {
            BlackjackRound round = round("10S", "6D", "7H", "5C");

            assertThat(round.isSettled()).isFalse();
            assertThat(round.isDealerHoleRevealed()).isFalse();
        }
    }

    @Nested
    @DisplayName("the hole card")
    class HoleCard {

        @Test
        @DisplayName("stays hidden, and the visible total reflects only the upcard")
        void holeCardStaysHidden() {
            BlackjackRound round = round("10S", "6D", "7H", "5C");

            assertThat(round.visibleDealerCards()).hasSize(1);
            assertThat(round.visibleDealerCards().get(0).code()).isEqualTo("6D");
            assertThat(round.dealerValue().total()).isEqualTo(6);
            assertThat(round.isDealerHoleRevealed()).isFalse();
        }

        @Test
        @DisplayName("is revealed once the round is settled")
        void holeCardRevealedOnSettle() {
            BlackjackRound round = round("10S", "6D", "7H", "5C", "10D");
            round.apply(PlayerAction.STAND, PLENTY);

            assertThat(round.isDealerHoleRevealed()).isTrue();
            assertThat(round.visibleDealerCards()).hasSizeGreaterThan(1);
        }
    }

    @Nested
    @DisplayName("dealer play")
    class DealerPlay {

        @Test
        @DisplayName("the dealer stands on soft 17 at an S17 table")
        void dealerStandsOnSoftSeventeen() {
            // Dealer shows an ace with a six in the hole: soft 17.
            BlackjackRound round = round("10S", "AD", "7H", "6C");
            round.apply(PlayerAction.STAND, PLENTY);

            assertThat(round.dealerValue().total()).isEqualTo(17);
            assertThat(round.visibleDealerCards()).hasSize(2);
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.PUSH);
        }

        @Test
        @DisplayName("the dealer hits soft 17 when the table says H17")
        void dealerHitsSoftSeventeenWhenConfigured() {
            BlackjackRules h17 = new BlackjackRules(8, 0.75, true, 1.5, 3, true);
            BlackjackRound round = new BlackjackRound(
                    h17, StackedCardSource.of("10S", "AD", "7H", "6C", "5H", "10D"), BET);

            round.apply(PlayerAction.STAND, PLENTY);

            // Soft 17 -> hit 5 -> hard 12 -> hit 10 -> bust at 22.
            assertThat(round.dealerValue().isBust()).isTrue();
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.WIN);
        }

        @Test
        @DisplayName("the dealer does not draw at all once every player hand has busted")
        void dealerDoesNotDrawAgainstABustedTable() {
            BlackjackRound round = round("10S", "6D", "5H", "7C", "KH");

            round.apply(PlayerAction.HIT, PLENTY); // 15 -> 25, bust

            assertThat(round.hands().get(0).status()).isEqualTo(HandStatus.BUST);
            assertThat(round.isSettled()).isTrue();
            // Dealer still holds only the original two cards: there was nothing left to beat.
            assertThat(round.visibleDealerCards()).hasSize(2);
            assertThat(round.netResult()).isEqualByComparingTo("-10.00");
        }

        @Test
        @DisplayName("a dealer bust pays every standing hand")
        void dealerBustPaysStandingHands() {
            BlackjackRound round = round("10S", "6D", "8H", "7C", "10D");
            round.apply(PlayerAction.STAND, PLENTY); // stands on 18; dealer 13 -> 23

            assertThat(round.dealerValue().isBust()).isTrue();
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.WIN);
            assertThat(round.totalPayout()).isEqualByComparingTo("20.00");
        }
    }

    @Nested
    @DisplayName("doubling")
    class Doubling {

        @Test
        @DisplayName("a double takes one card, doubles the stake and ends the hand")
        void doubleTakesExactlyOneCard() {
            BlackjackRound round = round("5S", "10D", "6H", "7C", "10H");

            round.apply(PlayerAction.DOUBLE, PLENTY);

            BlackjackHand hand = round.hands().get(0);
            assertThat(hand.isDoubled()).isTrue();
            assertThat(hand.cards()).hasSize(3);
            assertThat(hand.bet()).isEqualByComparingTo("20.00");
            assertThat(round.totalStaked()).isEqualByComparingTo("20.00");
            // 21 against the dealer's 17.
            assertThat(hand.outcome()).isEqualTo(HandOutcome.WIN);
            assertThat(round.totalPayout()).isEqualByComparingTo("40.00");
            assertThat(round.netResult()).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("doubling is offered only on the first two cards")
        void doubleUnavailableAfterHitting() {
            BlackjackRound round = round("5S", "10D", "4H", "7C", "3D");

            assertThat(round.legalActions(PLENTY)).contains(PlayerAction.DOUBLE);
            round.apply(PlayerAction.HIT, PLENTY);
            assertThat(round.legalActions(PLENTY)).doesNotContain(PlayerAction.DOUBLE);
        }

        @Test
        @DisplayName("doubling is withheld when the player cannot cover the extra stake")
        void doubleRequiresCoveringBalance() {
            BlackjackRound round = round("5S", "10D", "6H", "7C", "10H");

            assertThat(round.legalActions(Money.of("9.99"))).doesNotContain(PlayerAction.DOUBLE);
            assertThat(round.legalActions(Money.of("10.00"))).contains(PlayerAction.DOUBLE);
        }
    }

    @Nested
    @DisplayName("splitting")
    class Splitting {

        @Test
        @DisplayName("a split makes two hands, each with its own stake, played in order")
        void splitCreatesTwoIndependentHands() {
            BlackjackRound round = round("8S", "9D", "8H", "7C", "10H", "3D", "10C", "5S");

            round.apply(PlayerAction.SPLIT, PLENTY);
            assertThat(round.hands()).hasSize(2);
            assertThat(round.totalStaked()).isEqualByComparingTo("20.00");
            assertThat(round.hands().get(0).cards()).hasSize(2); // 8 + 10 = 18
            assertThat(round.hands().get(1).cards()).hasSize(2); // 8 + 3 = 11

            round.apply(PlayerAction.STAND, PLENTY);        // stand the 18
            assertThat(round.activeHandIndex()).isEqualTo(1);
            round.apply(PlayerAction.HIT, PLENTY);          // 11 + 10 = 21, auto-stands

            // Dealer 16 draws a 5 for 21: the 18 loses, the 21 pushes.
            assertThat(round.hands().get(0).outcome()).isEqualTo(HandOutcome.LOSE);
            assertThat(round.hands().get(1).outcome()).isEqualTo(HandOutcome.PUSH);
            assertThat(round.totalPayout()).isEqualByComparingTo("10.00");
            assertThat(round.netResult()).isEqualByComparingTo("-10.00");
        }

        @Test
        @DisplayName("split aces receive exactly one card each and cannot be played on")
        void splitAcesGetOneCardEach() {
            BlackjackRound round = round("AS", "9D", "AH", "7C", "10H", "10D", "5C");

            round.apply(PlayerAction.SPLIT, PLENTY);

            // Both hands are finished the moment the split happens, so the round settles.
            assertThat(round.isSettled()).isTrue();
            assertThat(round.hands()).hasSize(2);
            assertThat(round.hands()).allSatisfy(hand -> {
                assertThat(hand.cards()).hasSize(2);
                assertThat(hand.status()).isEqualTo(HandStatus.STAND);
                assertThat(hand.isSplitAce()).isTrue();
            });
            assertThat(round.legalActions(PLENTY)).isEmpty();
        }

        @Test
        @DisplayName("21 made after a split is an ordinary 21, not a 3:2 natural")
        void twentyOneAfterSplitIsNotANatural() {
            BlackjackRound round = round("AS", "9D", "AH", "6C", "10H", "10D", "10S");

            round.apply(PlayerAction.SPLIT, PLENTY);

            assertThat(round.hands()).allSatisfy(hand -> {
                assertThat(hand.value().total()).isEqualTo(21);
                assertThat(hand.isNaturalBlackjack()).isFalse();
                assertThat(hand.outcome()).isEqualTo(HandOutcome.WIN);
            });
            // Two 20.00 wins at even money. Paid as naturals it would have been 50.00.
            assertThat(round.totalStaked()).isEqualByComparingTo("20.00");
            assertThat(round.totalPayout()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("splitting needs a matching pair and enough balance to back it")
        void splitRequiresPairAndBalance() {
            BlackjackRound pair = round("8S", "9D", "8H", "7C", "2D", "3D");
            assertThat(pair.legalActions(PLENTY)).contains(PlayerAction.SPLIT);
            assertThat(pair.legalActions(Money.of("5.00"))).doesNotContain(PlayerAction.SPLIT);

            BlackjackRound notAPair = round("8S", "9D", "7H", "7C", "2D", "3D");
            assertThat(notAPair.legalActions(PLENTY)).doesNotContain(PlayerAction.SPLIT);
        }

        @Test
        @DisplayName("any two ten-valued cards may be split, as at a real table")
        void tenValuedCardsAreSplittable() {
            BlackjackRound round = round("KS", "9D", "QH", "7C", "2D", "3D");

            assertThat(round.legalActions(PLENTY)).contains(PlayerAction.SPLIT);
        }

        @Test
        @DisplayName("splitting stops at the table maximum of four hands")
        void splitLimitIsEnforced() {
            // Every new hand pairs up again, so only the rule limit can stop the resplitting.
            BlackjackRound round = round(
                    "8S", "9D", "8H", "7C",
                    "8D", "8C", "8H", "8S", "8D", "8C", "2H", "3D", "4C", "5S", "6D");

            round.apply(PlayerAction.SPLIT, PLENTY);
            round.apply(PlayerAction.SPLIT, PLENTY);
            round.apply(PlayerAction.SPLIT, PLENTY);

            assertThat(round.hands()).hasSize(4);
            assertThat(round.legalActions(PLENTY)).doesNotContain(PlayerAction.SPLIT);
        }
    }

    @Nested
    @DisplayName("input validation")
    class Validation {

        @Test
        @DisplayName("an action that is not currently legal is rejected")
        void illegalActionIsRejected() {
            BlackjackRound round = round("8S", "9D", "5H", "7C", "2D");

            assertThatThrownBy(() -> round.apply(PlayerAction.SPLIT, PLENTY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Illegal action SPLIT");
        }

        @Test
        @DisplayName("a settled round accepts no further actions")
        void settledRoundRejectsActions() {
            BlackjackRound round = round("AS", "9D", "KH", "7C");
            assertThat(round.isSettled()).isTrue();

            assertThatThrownBy(() -> round.apply(PlayerAction.HIT, PLENTY))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already settled");
        }

        @Test
        @DisplayName("hitting to exactly 21 stands automatically rather than inviting a bust")
        void hittingToTwentyOneStands() {
            BlackjackRound round = round("10S", "6D", "5H", "7C", "6H", "10D");

            round.apply(PlayerAction.HIT, PLENTY); // 15 + 6 = 21

            assertThat(round.hands().get(0).value().total()).isEqualTo(21);
            assertThat(round.hands().get(0).status()).isEqualTo(HandStatus.STAND);
        }
    }
}
