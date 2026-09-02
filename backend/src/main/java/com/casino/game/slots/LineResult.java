package com.casino.game.slots;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one lit payline paid.
 *
 * @param payline     which line this is, so the UI can draw it across the window
 * @param symbols     the three symbols the line reads, in reel order
 * @param multiplier  payout multiplier on the per-line bet; 0 when the line did not pay
 * @param payout      returned on this line, stake included
 * @param combination human-readable name of the win, or "No win"
 */
public record LineResult(
        SlotPayline payline,
        List<SlotSymbol> symbols,
        int multiplier,
        BigDecimal payout,
        String combination) {

    public boolean isWin() {
        return multiplier > 0;
    }
}
