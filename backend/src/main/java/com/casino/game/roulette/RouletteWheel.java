package com.casino.game.roulette;

import com.casino.game.common.RandomSource;
import java.util.List;
import java.util.Set;

/**
 * An American double-zero roulette wheel.
 *
 * <p>{@link #POCKET_ORDER} is the real physical sequence found on an American wheel, running
 * clockwise from the zero. It is not the European order with a 00 dropped into it: the two are
 * different wheels. On this one the greens sit directly opposite each other, and the numbers
 * are arranged in consecutive pairs across the diameter.
 *
 * <p>Two green pockets means 38 pockets against payouts priced for 36, which is where the house
 * edge of 2/38 = 5.26% comes from. That holds for every bet on the cloth bar one: the five-number
 * {@link RouletteBetType#TOP_LINE}, which pays 6:1 on a bet covering 5 of 38, and so runs at
 * 7.89%. It is the worst bet on the table and the only one an American layout adds.
 *
 * <p>On representing the double zero: a pocket is an {@code int} throughout the game, and 0 and
 * 00 are the same integer. Rather than thread a string through every payout and settlement path,
 * the double zero is held as {@link #DOUBLE_ZERO} and written out through {@link #label(int)} at
 * the two boundaries that show it to anyone -- the API response and the ledger. {@code 37} is
 * otherwise not a pocket, and {@link #parsePocket(String)} refuses it by that name so a bet
 * cannot reach the double zero except by asking for "00".
 *
 * <p>On randomness: the winning pocket is drawn uniformly from a cryptographic RNG rather than
 * from a simulation of ball and rotor dynamics. A deterministic physics model would be
 * <em>less</em> faithful, not more: real wheels are painstakingly balanced precisely so that
 * outcomes are uniform, and any simulation detailed enough to be interesting would also be
 * predictable from its inputs. Uniform-over-38 is the behaviour a true wheel is built to have.
 */
public final class RouletteWheel {

    /** The double zero, as an int. Written "00"; see the note on representation above. */
    public static final int DOUBLE_ZERO = 37;

    public static final List<Integer> POCKET_ORDER = List.of(
            0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
            DOUBLE_ZERO, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2);

    public static final int POCKET_COUNT = 38;

    /**
     * The pockets no outside bet covers. They are the house edge: 2 in 38, or 5.26%.
     */
    public static final int GREEN_POCKETS = 2;

    /** The highest numbered pocket that is an actual number, as opposed to a green. */
    public static final int HIGHEST_NUMBER = 36;

    private static final Set<Integer> RED_NUMBERS = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private final RandomSource random;

    public RouletteWheel(RandomSource random) {
        this.random = random;
    }

    /** Spins and returns the winning pocket, uniform over all 38 pockets. */
    public int spin() {
        return POCKET_ORDER.get(random.nextInt(POCKET_ORDER.size()));
    }

    public static PocketColor colorOf(int pocket) {
        if (pocket == 0 || pocket == DOUBLE_ZERO) {
            return PocketColor.GREEN;
        }
        return RED_NUMBERS.contains(pocket) ? PocketColor.RED : PocketColor.BLACK;
    }

    public static boolean isGreen(int pocket) {
        return pocket == 0 || pocket == DOUBLE_ZERO;
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
        return (pocket >= 0 && pocket <= HIGHEST_NUMBER) || pocket == DOUBLE_ZERO;
    }

    /** How a pocket is written on the cloth. The only place 37 becomes "00". */
    public static String label(int pocket) {
        return pocket == DOUBLE_ZERO ? "00" : Integer.toString(pocket);
    }

    /**
     * Reads a pocket written as it appears on the cloth.
     *
     * <p>Strict on purpose. "37" is refused even though that is how the double zero is held
     * internally, so the only way to bet the double zero is to name it "00"; and a leading zero
     * is refused so "00" cannot be reached by "000" and 0 cannot be reached by "0".
     *
     * @throws IllegalArgumentException if the text does not name a pocket
     */
    public static int parsePocket(String text) {
        String trimmed = text == null ? "" : text.trim();
        if ("00".equals(trimmed)) {
            return DOUBLE_ZERO;
        }
        if (trimmed.length() > 1 && trimmed.startsWith("0")) {
            throw new IllegalArgumentException("Not a pocket on this wheel: " + trimmed);
        }
        int value;
        try {
            value = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Selection must be whole numbers: " + trimmed);
        }
        if (value < 0 || value > HIGHEST_NUMBER) {
            throw new IllegalArgumentException("Not a pocket on this wheel: " + trimmed);
        }
        return value;
    }
}
