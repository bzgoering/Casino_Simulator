package com.casino.game.roulette;

import static org.assertj.core.api.Assertions.assertThat;

import com.casino.game.common.SeededRandomSource;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouletteWheelTest {

    @Test
    @DisplayName("the wheel is a single-zero European wheel of 37 distinct pockets")
    void wheelHasThirtySevenDistinctPockets() {
        assertThat(RouletteWheel.POCKET_ORDER).hasSize(37);
        assertThat(new HashSet<>(RouletteWheel.POCKET_ORDER)).hasSize(37);
        assertThat(RouletteWheel.POCKET_ORDER).contains(0).doesNotContain(37);
        assertThat(RouletteWheel.POCKET_COUNT).isEqualTo(37);
    }

    @Test
    @DisplayName("the pocket sequence matches a real European wheel")
    void pocketOrderMatchesRealWheel() {
        // The physical clockwise order from the zero. Neighbour and sector bets depend on it.
        assertThat(RouletteWheel.POCKET_ORDER).containsExactly(
                0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
                10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26);
    }

    @Test
    @DisplayName("colours split 18 red, 18 black, with a green zero")
    void coloursAreCorrect() {
        assertThat(RouletteWheel.colorOf(0)).isEqualTo(PocketColor.GREEN);

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
    @DisplayName("red and black alternate around the wheel, ignoring the zero")
    void redAndBlackAlternate() {
        List<Integer> order = RouletteWheel.POCKET_ORDER;
        for (int i = 1; i < order.size() - 1; i++) {
            PocketColor current = RouletteWheel.colorOf(order.get(i));
            PocketColor next = RouletteWheel.colorOf(order.get(i + 1));
            assertThat(current).isNotEqualTo(next);
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

        assertThat(seen).hasSize(37);
    }

    @Test
    @DisplayName("the wheel is uniform: no pocket is favoured over many spins")
    void spinsAreUniform() {
        RouletteWheel wheel = new RouletteWheel(new SeededRandomSource(7L));
        int spins = 370_000;
        int[] counts = new int[37];

        for (int i = 0; i < spins; i++) {
            counts[wheel.spin()]++;
        }

        int expected = spins / 37;
        for (int pocket = 0; pocket < 37; pocket++) {
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
        assertThat(RouletteWheel.wheelIndexOf(26)).isEqualTo(36);
        assertThat(RouletteWheel.wheelIndexOf(32)).isEqualTo(1);
    }
}
