package com.casino.game.roulette;

import java.math.BigDecimal;

/** Per-bet settlement detail, so the UI can show which chips won and by how much. */
public record RouletteBetResult(
        RouletteBetType type,
        String selection,
        BigDecimal amount,
        boolean won,
        BigDecimal payout) {
}
