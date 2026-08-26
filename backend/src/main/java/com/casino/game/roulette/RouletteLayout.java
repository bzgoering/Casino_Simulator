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
 * row {@code (n-1)/3}, column {@code (n-1)%3}.
 */
public final class RouletteLayout {

    public static final int ROWS = 12;
    public static final int COLUMNS = 3;

    private RouletteLayout() {
    }

    public static int rowOf(int number) {
        return (number - 1) / COLUMNS;
    }

    public static int columnOf(int number) {
        return (number - 1) % COLUMNS;
    }

    /** True when the two numbers share an edge on the cloth, including the zero splits. */
    public static boolean isValidSplit(Set<Integer> numbers) {
        if (numbers.size() != 2) {
            return false;
        }
        var sorted = new TreeSet<>(numbers);
        int low = sorted.first();
        int high = sorted.last();

        // 0 is adjacent to 1, 2 and 3 on a European cloth.
        if (low == 0) {
            return high == 1 || high == 2 || high == 3;
        }
        boolean sameRowAdjacent = rowOf(low) == rowOf(high) && Math.abs(columnOf(low) - columnOf(high)) == 1;
        boolean sameColumnAdjacent = columnOf(low) == columnOf(high) && Math.abs(rowOf(low) - rowOf(high)) == 1;
        return sameRowAdjacent || sameColumnAdjacent;
    }

    /** A row of three, or one of the two trios that include the zero. */
    public static boolean isValidStreet(Set<Integer> numbers) {
        if (numbers.size() != 3) {
            return false;
        }
        if (numbers.equals(Set.of(0, 1, 2)) || numbers.equals(Set.of(0, 2, 3))) {
            return true;
        }
        var sorted = new TreeSet<>(numbers);
        int first = sorted.first();
        if (first < 1 || columnOf(first) != 0) {
            return false;
        }
        return numbers.equals(Set.of(first, first + 1, first + 2));
    }

    /** A square of four, or the 0-1-2-3 basket. */
    public static boolean isValidCorner(Set<Integer> numbers) {
        if (numbers.size() != 4) {
            return false;
        }
        if (numbers.equals(Set.of(0, 1, 2, 3))) {
            return true;
        }
        var sorted = new TreeSet<>(numbers);
        int topLeft = sorted.first();
        if (topLeft < 1 || columnOf(topLeft) == COLUMNS - 1 || rowOf(topLeft) >= ROWS - 1) {
            return false;
        }
        return numbers.equals(Set.of(topLeft, topLeft + 1, topLeft + 3, topLeft + 4));
    }

    /** Two adjacent streets, six numbers starting at the left column. */
    public static boolean isValidSixLine(Set<Integer> numbers) {
        if (numbers.size() != 6) {
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
