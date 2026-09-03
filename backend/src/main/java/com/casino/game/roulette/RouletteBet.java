package com.casino.game.roulette;

import com.casino.game.common.Money;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A validated bet on the table.
 *
 * <p>Instances can only be produced through {@link #of}, which resolves the selection into the
 * exact set of pockets it covers and rejects anything that is not placeable on a real cloth.
 * Once constructed, a bet is guaranteed to cover exactly {@link RouletteBetType#selectionSize()}
 * pockets, so its payout odds are honest.
 *
 * @param type    the kind of bet
 * @param selection the raw selection as submitted, kept for display and the round log
 * @param pockets the pockets this bet covers
 * @param amount  the stake
 */
public record RouletteBet(RouletteBetType type, String selection, Set<Integer> pockets, BigDecimal amount) {

    public RouletteBet {
        pockets = Set.copyOf(pockets);
        amount = Money.scaled(amount);
    }

    /**
     * Builds and validates a bet.
     *
     * @param selection for inside bets, comma-separated pockets as written on the cloth
     *                  ("17", "17,20", or "0,00,1,2,3" for the five-number bet);
     *                  for outside bets a keyword: RED/BLACK, ODD/EVEN, LOW/HIGH, or 1/2/3
     *                  for a dozen or column
     * @throws IllegalArgumentException if the selection is not a legal bet of that type
     */
    public static RouletteBet of(RouletteBetType type, String selection, BigDecimal amount) {
        if (!Money.isPositive(amount)) {
            throw new IllegalArgumentException("Bet amount must be positive");
        }
        String normalised = selection == null ? "" : selection.trim().toUpperCase(Locale.ROOT);
        Set<Integer> pockets = type.isInsideBet()
                ? insidePockets(type, normalised)
                : outsidePockets(type, normalised);

        if (pockets.size() != type.selectionSize()) {
            throw new IllegalArgumentException(
                    type + " must cover exactly " + type.selectionSize() + " pockets");
        }
        return new RouletteBet(type, normalised, pockets, amount);
    }

    private static Set<Integer> insidePockets(RouletteBetType type, String selection) {
        Set<Integer> numbers = parseNumbers(selection);
        boolean valid = switch (type) {
            case STRAIGHT -> numbers.size() == 1;
            case SPLIT -> RouletteLayout.isValidSplit(numbers);
            case STREET -> RouletteLayout.isValidStreet(numbers);
            case CORNER -> RouletteLayout.isValidCorner(numbers);
            case SIX_LINE -> RouletteLayout.isValidSixLine(numbers);
            case TOP_LINE -> RouletteLayout.isValidTopLine(numbers);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Numbers " + new java.util.TreeSet<>(numbers) + " are not a valid " + type + " on the layout");
        }
        return numbers;
    }

    private static Set<Integer> parseNumbers(String selection) {
        if (selection.isEmpty()) {
            throw new IllegalArgumentException("Bet selection is required");
        }
        Set<Integer> numbers = new HashSet<>();
        // -1 keeps trailing empty components, so "17," is rejected rather than silently
        // parsing as a bare 17. Malformed input should be refused, not guessed at.
        String[] parts = selection.split(",", -1);
        // Cap the input length before parsing so a huge payload cannot be used to burn CPU.
        if (parts.length > RouletteBetType.SIX_LINE.selectionSize()) {
            throw new IllegalArgumentException("Too many numbers in selection");
        }
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Selection has an empty entry");
            }
            // The wheel owns what a pocket may be called, so that "00" means the double zero
            // and "37" -- how it happens to be held internally -- names nothing at all.
            int value = RouletteWheel.parsePocket(part);
            if (!numbers.add(value)) {
                throw new IllegalArgumentException("Duplicate number in selection: " + value);
            }
        }
        return numbers;
    }

    private static Set<Integer> outsidePockets(RouletteBetType type, String selection) {
        return switch (type) {
            case COLOR -> switch (selection) {
                case "RED" -> pocketsWithColor(PocketColor.RED);
                case "BLACK" -> pocketsWithColor(PocketColor.BLACK);
                default -> throw new IllegalArgumentException("Colour bet must be RED or BLACK");
            };
            case PARITY -> switch (selection) {
                case "ODD" -> numbersMatching(n -> n % 2 == 1);
                case "EVEN" -> numbersMatching(n -> n % 2 == 0);
                default -> throw new IllegalArgumentException("Parity bet must be ODD or EVEN");
            };
            case HALF -> switch (selection) {
                case "LOW" -> numbersMatching(n -> n <= 18);
                case "HIGH" -> numbersMatching(n -> n >= 19);
                default -> throw new IllegalArgumentException("Half bet must be LOW or HIGH");
            };
            case DOZEN -> RouletteLayout.dozen(parseIndex(selection, "Dozen"));
            case COLUMN -> RouletteLayout.column(parseIndex(selection, "Column"));
            default -> throw new IllegalArgumentException("Unsupported outside bet: " + type);
        };
    }

    private static int parseIndex(String selection, String label) {
        try {
            return Integer.parseInt(selection);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " bet must be 1, 2 or 3");
        }
    }

    private static Set<Integer> pocketsWithColor(PocketColor color) {
        return numbersMatching(n -> RouletteWheel.colorOf(n) == color);
    }

    private static Set<Integer> numbersMatching(java.util.function.IntPredicate predicate) {
        Set<Integer> numbers = new HashSet<>();
        // Both greens are deliberately excluded: they lose every outside bet, and on this wheel
        // there are two of them, which is exactly why the edge is 5.26% and not 2.70%.
        for (int n = 1; n <= RouletteWheel.HIGHEST_NUMBER; n++) {
            if (predicate.test(n)) {
                numbers.add(n);
            }
        }
        return numbers;
    }

    public boolean wins(int pocket) {
        return pockets.contains(pocket);
    }

    /** Total returned on a win, stake included. Zero on a loss. */
    public BigDecimal payoutFor(int pocket) {
        if (!wins(pocket)) {
            return Money.ZERO;
        }
        return Money.scaled(amount.multiply(BigDecimal.valueOf(type.payoutToOne() + 1L)));
    }
}
