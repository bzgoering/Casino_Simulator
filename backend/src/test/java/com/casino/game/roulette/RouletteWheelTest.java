package com.casino.game.roulette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.casino.game.common.SeededRandomSource;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouletteWheelTest {

    @Test
    @DisplayName("the wheel is a double-zero American wheel of 38 distinct pockets")
    void wheelHasThirtyEightDistinctPockets() {
        assertThat(RouletteWheel.POCKET_ORDER).hasSize(38);
        assertThat(new HashSet<>(RouletteWheel.POCKET_ORDER)).hasSize(38);
        assertThat(RouletteWheel.POCKET_ORDER).contains(0).contains(RouletteWheel.DOUBLE_ZERO);
        assertThat(RouletteWheel.POCKET_COUNT).isEqualTo(38);
        assertThat(RouletteWheel.GREEN_POCKETS).isEqualTo(2);
    }

    @Test
    @DisplayName("the double zero is written 00, and 37 names nothing")
    void doubleZeroIsWrittenAsZeroZero() {
        assertThat(RouletteWheel.label(RouletteWheel.DOUBLE_ZERO)).isEqualTo("00");
        assertThat(RouletteWheel.label(0)).isEqualTo("0");
        assertThat(RouletteWheel.label(17)).isEqualTo("17");

        assertThat(RouletteWheel.parsePocket("00")).isEqualTo(RouletteWheel.DOUBLE_ZERO);
        assertThat(RouletteWheel.parsePocket("0")).isZero();
        assertThat(RouletteWheel.parsePocket("17")).isEqualTo(17);

        // The internal spelling must not be a way in: a bet may reach the double zero only by
        // naming it the way the cloth does.
        assertThatThrownBy(() -> RouletteWheel.parsePocket("37"))
                .isInstanceOf(IllegalArgumentException.class);
        // Nor may padding alias one pocket onto another.
        assertThatThrownBy(() -> RouletteWheel.parsePocket("000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RouletteWheel.parsePocket("017"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the two greens sit opposite each other, as they do on a real wheel")
    void greensAreOpposite() {
        int zero = RouletteWheel.wheelIndexOf(0);
        int doubleZero = RouletteWheel.wheelIndexOf(RouletteWheel.DOUBLE_ZERO);

        assertThat(Math.abs(doubleZero - zero)).isEqualTo(RouletteWheel.POCKET_COUNT / 2);
    }

    @Test
    @DisplayName("the pocket sequence matches a real American wheel")
    void pocketOrderMatchesRealWheel() {
        // The physical clockwise order from the zero. This is a different wheel from the
        // European one, not that one with a 00 inserted into it.
        assertThat(RouletteWheel.POCKET_ORDER).containsExactly(
                0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
                37, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2);
    }

    @Test
    @DisplayName("colours split 18 red, 18 black, with two greens")
    void coloursAreCorrect() {
        assertThat(RouletteWheel.colorOf(0)).isEqualTo(PocketColor.GREEN);
        assertThat(RouletteWheel.colorOf(RouletteWheel.DOUBLE_ZERO)).isEqualTo(PocketColor.GREEN);

        long red = 0;
        long black = 0;
        for (int n = 1; n <= 36; n++) {
            if (RouletteWheel.colorOf(n) == PocketColor.RED) {
                red++;
            } else {
                black++;
            }
        }
        assertThat(red).isEqualTo(18);
        assertThat(black).isEqualTo(18);

        // Spot-check against the real cloth.
        assertThat(RouletteWheel.colorOf(1)).isEqualTo(PocketColor.RED);
        assertThat(RouletteWheel.colorOf(2)).isEqualTo(PocketColor.BLACK);
        assertThat(RouletteWheel.colorOf(19)).isEqualTo(PocketColor.RED);
        assertThat(RouletteWheel.colorOf(20)).isEqualTo(PocketColor.BLACK);
    }

    @Test
    @DisplayName("red and black alternate around the wheel, except where a green breaks it")
    void redAndBlackAlternate() {
        List<Integer> order = RouletteWheel.POCKET_ORDER;
        for (int i = 0; i < order.size(); i++) {
            int current = order.get(i);
            int next = order.get((i + 1) % order.size());
            // Each green is flanked by two of the same colour on a real American wheel, so the
            // alternation is only claimed between neighbours that are both numbers.
            if (RouletteWheel.isGreen(current) || RouletteWheel.isGreen(next)) {
                continue;
            }
            assertThat(RouletteWheel.colorOf(current))
                    .as("pockets %d and %d", current, next)
                    .isNotEqualTo(RouletteWheel.colorOf(next));
        }
    }

    @Test
    @DisplayName("every spin lands in a real pocket, and every pocket is reachable")
    void spinsCoverEveryPocket() {
        RouletteWheel wheel = new RouletteWheel(new SeededRandomSource(2026L));
        var seen = new HashSet<Integer>();

        for (int i = 0; i < 20_000; i++) {
            int pocket = wheel.spin();
            assertThat(RouletteWheel.isValidPocket(pocket)).isTrue();
            seen.add(pocket);
        }

        assertThat(seen).hasSize(38);
    }

    @Test
    @DisplayName("the wheel is uniform: no pocket is favoured over many spins")
    void spinsAreUniform() {
        RouletteWheel wheel = new RouletteWheel(new SeededRandomSource(7L));
        int spins = 380_000;
        int[] counts = new int[38];

        for (int i = 0; i < spins; i++) {
            counts[wheel.spin()]++;
        }

        int expected = spins / 38;
        for (int pocket = 0; pocket < 38; pocket++) {
            // Well inside the noise for 10,000 expected hits per pocket.
            assertThat(counts[pocket])
                    .as("pocket %d", pocket)
                    .isBetween((int) (expected * 0.9), (int) (expected * 1.1));
        }
    }

    @Test
    @DisplayName("wheel index lookup is consistent with the pocket order")
    void wheelIndexLookup() {
        assertThat(RouletteWheel.wheelIndexOf(0)).isZero();
        assertThat(RouletteWheel.wheelIndexOf(28)).isEqualTo(1);
        assertThat(RouletteWheel.wheelIndexOf(RouletteWheel.DOUBLE_ZERO)).isEqualTo(19);
        assertThat(RouletteWheel.wheelIndexOf(2)).isEqualTo(37);
    }
}
