package com.casino.game.slots;

import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome of one spin.
 *
 * <p>The whole window is reported, not just the lines that were paid. A player who lit one line
 * is entitled to see the near miss on the diagonal they did not buy, which is exactly what the
 * glass on a real machine shows them.
 *
 * @param stops       the stop index per reel, the symbol that landed on the centre row
 * @param window      three symbols per reel, top row first, in reel order
 * @param lines       one entry per line the player lit, in activation order
 * @param betPerLine  the stake on each lit line
 * @param credits     lines lit, and so the number of entries in {@code lines}
 * @param totalStaked {@code betPerLine} times {@code credits}
 * @param payout      total returned across every line, stakes included
 * @param net         payout minus the total staked; negative on a losing spin
 */
public record SpinResult(
        List<Integer> stops,
        List<List<SlotSymbol>> window,
        List<LineResult> lines,
        BigDecimal betPerLine,
        int credits,
        BigDecimal totalStaked,
        BigDecimal payout,
        BigDecimal net) {

    public boolean isWin() {
        return lines.stream().anyMatch(LineResult::isWin);
    }

    /** The lines that actually paid, for the round log and the UI. */
    public List<LineResult> winningLines() {
        return lines.stream().filter(LineResult::isWin).toList();
    }

    /** Combined multiplier across every lit line, for a one-line summary of the spin. */
    public int totalMultiplier() {
        return lines.stream().mapToInt(LineResult::multiplier).sum();
    }

    /**
     * A short description of the spin: the single win, or how many lines paid.
     */
    public String describe() {
        List<LineResult> winners = winningLines();
        if (winners.isEmpty()) {
            return "No win";
        }
        if (winners.size() == 1) {
            LineResult only = winners.get(0);
            return only.combination() + " on the " + only.payline().displayName().toLowerCase();
        }
        return winners.size() + " winning lines";
    }
}
