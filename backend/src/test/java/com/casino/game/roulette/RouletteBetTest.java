package com.casino.game.roulette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casino.game.common.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RouletteBetTest {

    private static final BigDecimal TEN = Money.of("10.00");

    private static RouletteBet bet(RouletteBetType type, String selection) {
        return RouletteBet.of(type, selection, TEN);
    }

    @Nested
    @DisplayName("payouts")
    class Payouts {

        @Test
        @DisplayName("a straight-up pays 35:1, returning 36 times the stake")
        void straightUpPays() {
            RouletteBet straight = bet(RouletteBetType.STRAIGHT, "17");

            assertThat(straight.wins(17)).isTrue();
            assertThat(straight.payoutFor(17)).isEqualByComparingTo("360.00");
            assertThat(straight.payoutFor(18)).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("outside bets pay even money and always lose to the zero")
        void outsideBetsLoseToZero() {
            RouletteBet red = bet(RouletteBetType.COLOR, "RED");
            RouletteBet even = bet(RouletteBetType.PARITY, "EVEN");
            RouletteBet low = bet(RouletteBetType.HALF, "LOW");

            assertThat(red.wins(0)).isFalse();
            assertThat(even.wins(0)).isFalse();
            assertThat(low.wins(0)).isFalse();

            assertThat(red.wins(1)).isTrue();
            assertThat(red.payoutFor(1)).isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("every bet type carries the same 2.70% house edge")
        void houseEdgeIsUniform() {
            for (RouletteBetType type : RouletteBetType.values()) {
                double winProbability = (double) type.selectionSize() / RouletteWheel.POCKET_COUNT;
                double expectedValue = winProbability * (type.payoutToOne() + 1) - 1.0;

                assertThat(-expectedValue)
                        .as("house edge for %s", type)
                        .isCloseTo(0.027027, org.assertj.core.data.Offset.offset(0.000001));
            }
        }
    }

    @Nested
    @DisplayName("layout validation")
    class LayoutValidation {

        @Test
        @DisplayName("a split must name two numbers that actually touch on the cloth")
        void splitMustBeAdjacent() {
            assertThat(bet(RouletteBetType.SPLIT, "1,2").pockets()).containsExactlyInAnyOrder(1, 2);
            assertThat(bet(RouletteBetType.SPLIT, "1,4").pockets()).containsExactlyInAnyOrder(1, 4);
            assertThat(bet(RouletteBetType.SPLIT, "0,1").pockets()).containsExactlyInAnyOrder(0, 1);

            // 1 and 5 sit diagonally, which is not a split.
            assertThatThrownBy(() -> bet(RouletteBetType.SPLIT, "1,5"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not a valid SPLIT");
            // 3 and 4 are on different rows and different columns.
            assertThatThrownBy(() -> bet(RouletteBetType.SPLIT, "3,4"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a street is one printed row of three")
        void streetMustBeARow() {
            assertThat(bet(RouletteBetType.STREET, "1,2,3").pockets()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(bet(RouletteBetType.STREET, "34,35,36").pockets()).containsExactlyInAnyOrder(34, 35, 36);
            assertThat(bet(RouletteBetType.STREET, "0,1,2").pockets()).containsExactlyInAnyOrder(0, 1, 2);

            assertThatThrownBy(() -> bet(RouletteBetType.STREET, "2,3,4"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a corner is a square of four touching numbers")
        void cornerMustBeASquare() {
            assertThat(bet(RouletteBetType.CORNER, "1,2,4,5").pockets())
                    .containsExactlyInAnyOrder(1, 2, 4, 5);
            assertThat(bet(RouletteBetType.CORNER, "0,1,2,3").pockets())
                    .containsExactlyInAnyOrder(0, 1, 2, 3);

            // Four numbers in a line are not a corner.
            assertThatThrownBy(() -> bet(RouletteBetType.CORNER, "1,2,3,4"))
                    .isInstanceOf(IllegalArgumentException.class);
            // A square starting in the right-hand column would run off the cloth.
            assertThatThrownBy(() -> bet(RouletteBetType.CORNER, "3,4,6,7"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a six line is two adjacent streets")
        void sixLineMustBeTwoStreets() {
            assertThat(bet(RouletteBetType.SIX_LINE, "1,2,3,4,5,6").pockets())
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);

            assertThatThrownBy(() -> bet(RouletteBetType.SIX_LINE, "2,3,4,5,6,7"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("outside bets resolve to the right group of numbers")
        void outsideBetsResolveCorrectly() {
            assertThat(bet(RouletteBetType.DOZEN, "1").pockets()).hasSize(12).contains(1, 12).doesNotContain(13);
            assertThat(bet(RouletteBetType.DOZEN, "3").pockets()).hasSize(12).contains(25, 36);
            assertThat(bet(RouletteBetType.COLUMN, "1").pockets()).hasSize(12).contains(1, 4, 34);
            assertThat(bet(RouletteBetType.HALF, "HIGH").pockets()).hasSize(18).contains(19, 36).doesNotContain(18);
        }
    }

    @Nested
    @DisplayName("rejecting forged bets")
    class ForgedBets {

        @Test
        @DisplayName("a split cannot be inflated to cover extra numbers at 17:1")
        void splitCannotCoverExtraNumbers() {
            // The whole point of validating the layout: this would pay 17:1 on 6 of 37 pockets.
            assertThatThrownBy(() -> bet(RouletteBetType.SPLIT, "1,2,3,4,5,6"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> bet(RouletteBetType.SPLIT, "1,2,3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a straight-up cannot name more than one number")
        void straightCannotNameSeveralNumbers() {
            assertThatThrownBy(() -> bet(RouletteBetType.STRAIGHT, "17,18"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an oversized selection is rejected before it is parsed")
        void oversizedSelectionIsRejected() {
            String huge = java.util.stream.IntStream.rangeClosed(1, 36)
                    .mapToObj(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));

            assertThatThrownBy(() -> bet(RouletteBetType.STRAIGHT, huge))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Too many numbers");
        }

        @ParameterizedTest
        @ValueSource(strings = {"37", "-1", "100", "999999"})
        @DisplayName("a number that is not on the wheel is rejected")
        void offWheelNumbersRejected(String selection) {
            assertThatThrownBy(() -> bet(RouletteBetType.STRAIGHT, selection))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not a pocket");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "abc", "1;2", "1.5", "17,", "null"})
        @DisplayName("a selection that is not whole numbers is rejected")
        void malformedSelectionsRejected(String selection) {
            assertThatThrownBy(() -> bet(RouletteBetType.STRAIGHT, selection))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("duplicate numbers cannot be used to pad a selection to the right size")
        void duplicateNumbersRejected() {
            assertThatThrownBy(() -> bet(RouletteBetType.SPLIT, "17,17"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate");
        }

        @Test
        @DisplayName("an unrecognised outside selection is rejected")
        void unknownOutsideSelectionsRejected() {
            assertThatThrownBy(() -> bet(RouletteBetType.COLOR, "GREEN"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> bet(RouletteBetType.DOZEN, "4"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> bet(RouletteBetType.COLUMN, "0"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a non-positive stake is rejected")
        void nonPositiveStakeRejected() {
            assertThatThrownBy(() -> RouletteBet.of(RouletteBetType.STRAIGHT, "17", Money.of("0.00")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
            assertThatThrownBy(() -> RouletteBet.of(RouletteBetType.STRAIGHT, "17", Money.of("-5.00")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("every constructed bet covers exactly the number of pockets its odds assume")
        void everyBetCoversItsDeclaredSize() {
            var samples = java.util.Map.of(
                    RouletteBetType.STRAIGHT, "5",
                    RouletteBetType.SPLIT, "5,8",
                    RouletteBetType.STREET, "4,5,6",
                    RouletteBetType.CORNER, "4,5,7,8",
                    RouletteBetType.SIX_LINE, "4,5,6,7,8,9",
                    RouletteBetType.COLUMN, "2",
                    RouletteBetType.DOZEN, "2",
                    RouletteBetType.COLOR, "BLACK",
                    RouletteBetType.PARITY, "ODD",
                    RouletteBetType.HALF, "LOW");

            samples.forEach((type, selection) ->
                    assertThat(bet(type, selection).pockets())
                            .as("%s covers %d pockets", type, type.selectionSize())
                            .hasSize(type.selectionSize()));
        }
    }
}
