package com.casino.game.slots;

import java.util.List;

/**
 * The lines a three-reel, three-row machine pays on.
 *
 * <p>Each constant names the row it reads on each reel, top row 0. Three straight-across lines
 * and the two diagonals, exactly as the glass on a real three-reel machine is printed.
 *
 * <p>Declaration order is activation order, and it is deliberate: a machine lights the centre
 * line first, then the outer rows, then the diagonals. Buying three credits therefore lights the
 * three straight lines and five lights everything, which is what the numbers printed on a real
 * cabinet mean.
 */
public enum SlotPayline {

    MIDDLE("Middle row", 1, 1, 1),
    TOP("Top row", 0, 0, 0),
    BOTTOM("Bottom row", 2, 2, 2),
    DIAGONAL_DOWN("Diagonal down", 0, 1, 2),
    DIAGONAL_UP("Diagonal up", 2, 1, 0);

    private final String displayName;
    private final List<Integer> rows;

    SlotPayline(String displayName, int firstReelRow, int secondReelRow, int thirdReelRow) {
        this.displayName = displayName;
        this.rows = List.of(firstReelRow, secondReelRow, thirdReelRow);
    }

    public String displayName() {
        return displayName;
    }

    /** The row this line reads on each reel, in reel order. */
    public List<Integer> rows() {
        return rows;
    }

    public int rowOnReel(int reel) {
        return rows.get(reel);
    }

    /**
     * The lines lit by buying {@code credits} credits: the first {@code credits} in declaration
     * order.
     *
     * @throws IllegalArgumentException when more credits are asked for than there are lines
     */
    public static List<SlotPayline> litBy(int credits) {
        if (credits < 1 || credits > values().length) {
            throw new IllegalArgumentException(
                    "credits must be between 1 and " + values().length);
        }
        return List.of(values()).subList(0, credits);
    }

    public static int count() {
        return values().length;
    }
}
