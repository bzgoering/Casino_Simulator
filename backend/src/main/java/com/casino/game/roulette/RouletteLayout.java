package com.casino.game.roulette;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Geometry of the betting cloth, used to validate that an inside bet is actually placeable.
 *
 * <p>This matters for more than tidiness. A split pays 17:1 because it covers exactly two
 * <em>adjacent</em> pockets; without this check a client could post a "split" naming twenty
 * numbers and collect 17:1 on it. Every inside bet is verified against the real layout before
 * any money moves.
 *
 * <p>The cloth runs 1-36 in twelve rows of three: number {@code n} sits at
 * row {@code (n-1)/3}, column {@code (n-1)%3}. The two greens sit side by side above it.
 *
 * <p>The greens are where an American cloth parts company with a European one, and the
 * difference is not decorative. There is no 0-1-2-3 corner here, because 00 stands between the
 * zero and the top of the grid; in its place is the five-number 0-00-1-2-3, which pays 6:1
 * rather than the 8:1 that corner paid. Accepting a European basket on this cloth would be
 * paying 8:1 on a bet the table prices at 6:1, so the old case is gone rather than loosened.
 */
public final class RouletteLayout {

    public static final int ROWS = 12;
    public static final int COLUMNS = 3;

    private static final int DOUBLE_ZERO = RouletteWheel.DOUBLE_ZERO;

    /** Every two-pocket bet that touches a green, as printed on an American cloth. */
    private static final Set<Set<Integer>> GREEN_SPLITS = Set.of(
            Set.of(0, DOUBLE_ZERO),
            Set.of(0, 1),
            Set.of(0, 2),
            Set.of(DOUBLE_ZERO, 2),
            Set.of(DOUBLE_ZERO, 3));

    /** Every three-pocket bet that touches a green. */
    private static final Set<Set<Integer>> GREEN_TRIOS = Set.of(
            Set.of(0, 1, 2),
            Set.of(0, DOUBLE_ZERO, 2),
            Set.of(DOUBLE_ZERO, 2, 3));

    /** The five-number bet, the only green bet wider than a trio on this cloth. */
    private static final Set<Integer> TOP_LINE = Set.of(0, DOUBLE_ZERO, 1, 2, 3);

    private static boolean touchesGreen(Set<Integer> numbers) {
        return numbers.stream().anyMatch(RouletteWheel::isGreen);
    }

    private RouletteLayout() {
    }

    public static int rowOf(int number) {
        return (number - 1) / COLUMNS;
    }

    public static int columnOf(int number) {
        return (number - 1) % COLUMNS;
    }

    /** True when the two numbers share an edge on the cloth, the green splits included. */
    public static boolean isValidSplit(Set<Integer> numbers) {
        if (numbers.size() != 2) {
            return false;
        }
        // A green has no row or column, so the arithmetic below cannot speak for it.
        if (touchesGreen(numbers)) {
            return GREEN_SPLITS.contains(numbers);
        }
        var sorted = new TreeSet<>(numbers);
        int low = sorted.first();
        int high = sorted.last();

        boolean sameRowAdjacent = rowOf(low) == rowOf(high) && Math.abs(columnOf(low) - columnOf(high)) == 1;
        boolean sameColumnAdjacent = columnOf(low) == columnOf(high) && Math.abs(rowOf(low) - rowOf(high)) == 1;
        return sameRowAdjacent || sameColumnAdjacent;
    }

    /** A row of three, or one of the three trios that include a green. */
    public static boolean isValidStreet(Set<Integer> numbers) {
        if (numbers.size() != 3) {
            return false;
        }
        if (touchesGreen(numbers)) {
            return GREEN_TRIOS.contains(numbers);
        }
        var sorted = new TreeSet<>(numbers);
        int first = sorted.first();
        if (first < 1 || columnOf(first) != 0) {
            return false;
        }
        return numbers.equals(Set.of(first, first + 1, first + 2));
    }

    /**
     * A square of four.
     *
     * <p>No green corner exists here. The European 0-1-2-3 basket paid 8:1; on this cloth those
     * pockets are part of the five-number bet at 6:1 instead, so honouring the old shape would
     * be overpaying a bet the table does not offer.
     */
    public static boolean isValidCorner(Set<Integer> numbers) {
        if (numbers.size() != 4 || touchesGreen(numbers)) {
            return false;
        }
        var sorted = new TreeSet<>(numbers);
        int topLeft = sorted.first();
        if (topLeft < 1 || columnOf(topLeft) == COLUMNS - 1 || rowOf(topLeft) >= ROWS - 1) {
            return false;
        }
        return numbers.equals(Set.of(topLeft, topLeft + 1, topLeft + 3, topLeft + 4));
    }

    /** The five-number bet: both greens and the first street, and nothing else. */
    public static boolean isValidTopLine(Set<Integer> numbers) {
        return TOP_LINE.equals(numbers);
    }

    /** Two adjacent streets, six numbers starting at the left column. */
    public static boolean isValidSixLine(Set<Integer> numbers) {
        if (numbers.size() != 6 || touchesGreen(numbers)) {
            return false;
        }
        var sorted = new TreeSet<>(numbers);
        int first = sorted.first();
        if (first < 1 || columnOf(first) != 0 || rowOf(first) >= ROWS - 1) {
            return false;
        }
        var expected = new HashSet<Integer>();
        for (int i = 0; i < 6; i++) {
            expected.add(first + i);
        }
        return numbers.equals(expected);
    }

    /** Column 1, 2 or 3 as printed on the cloth (the "2 to 1" boxes). */
    public static Set<Integer> column(int columnNumber) {
        if (columnNumber < 1 || columnNumber > COLUMNS) {
            throw new IllegalArgumentException("Column must be 1..3");
        }
        var numbers = new HashSet<Integer>();
        for (int n = columnNumber; n <= 36; n += COLUMNS) {
            numbers.add(n);
        }
        return numbers;
    }

    /** Dozen 1 (1-12), 2 (13-24) or 3 (25-36). */
    public static Set<Integer> dozen(int dozenNumber) {
        if (dozenNumber < 1 || dozenNumber > 3) {
            throw new IllegalArgumentException("Dozen must be 1..3");
        }
        var numbers = new HashSet<Integer>();
        int start = (dozenNumber - 1) * 12 + 1;
        for (int n = start; n < start + 12; n++) {
            numbers.add(n);
        }
        return numbers;
    }
}
