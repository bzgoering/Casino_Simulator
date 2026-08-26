package com.casino.game.roulette;

import com.casino.game.common.RandomSource;
import java.util.List;
import java.util.Set;

/**
 * A European single-zero roulette wheel.
 *
 * <p>{@link #POCKET_ORDER} is the real physical sequence found on a European wheel, running
 * clockwise from the zero. The order is what makes neighbour bets and wheel sectors
 * (voisins, tiers, orphelins) mean anything, so it is reproduced exactly rather than
 * being 0..36 in numeric order.
 *
 * <p>Single zero means 37 pockets against payouts priced for 36, which is where the house
 * edge of 1/37 = 2.70% comes from, uniformly across every bet type.
 *
 * <p>On randomness: the winning pocket is drawn uniformly from a cryptographic RNG rather than
 * from a simulation of ball and rotor dynamics. A deterministic physics model would be
 * <em>less</em> faithful, not more: real wheels are painstakingly balanced precisely so that
 * outcomes are uniform, and any simulation detailed enough to be interesting would also be
 * predictable from its inputs. Uniform-over-37 is the behaviour a true wheel is built to have.
 */
public final class RouletteWheel {

    public static final List<Integer> POCKET_ORDER = List.of(
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26);

    public static final int POCKET_COUNT = 37;

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private final RandomSource random;

    public RouletteWheel(RandomSource random) {
        this.random = random;
    }

    /** Spins and returns the winning pocket number, uniform over all 37 pockets. */
    public int spin() {
        return POCKET_ORDER.get(random.nextInt(POCKET_ORDER.size()));
    }

    public static PocketColor colorOf(int pocket) {
        if (pocket == 0) {
            return PocketColor.GREEN;
        }
        return RED_NUMBERS.contains(pocket) ? PocketColor.RED : PocketColor.BLACK;
    }

    /** Index of a pocket in the physical wheel order, for rendering the wheel and sector bets. */
    public static int wheelIndexOf(int pocket) {
        int index = POCKET_ORDER.indexOf(pocket);
        if (index < 0) {
            throw new IllegalArgumentException("Not a pocket on this wheel: " + pocket);
        }
        return index;
    }

    public static boolean isValidPocket(int pocket) {
        return pocket >= 0 && pocket <= 36;
    }
}
