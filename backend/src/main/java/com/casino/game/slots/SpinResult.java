package com.casino.game.slots;

import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome of one spin.
 *
 * @param stops      the stop index landed on for each reel, so the UI can animate to the right place
 * @param symbols    the symbol on the payline for each reel
 * @param multiplier payout multiplier on the line bet, 0 for a loss
 * @param payout     total returned to the player, stake included
 * @param net        payout minus stake; negative on a loss
 * @param combination human-readable name of the win, or "No win"
 */
public record SpinResult(
        List<Integer> stops,
        List<SlotSymbol> symbols,
        int multiplier,
        BigDecimal payout,
        BigDecimal net,
        String combination) {

    public boolean isWin() {
        return multiplier > 0;
    }
}
