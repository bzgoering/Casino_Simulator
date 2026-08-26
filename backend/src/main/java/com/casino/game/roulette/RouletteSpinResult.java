package com.casino.game.roulette;

import java.math.BigDecimal;
import java.util.List;

/**
 * The outcome of one spin across every bet placed on it.
 *
 * @param pocket      the winning number
 * @param color       its colour
 * @param wheelIndex  its position in the physical wheel order, for animating the wheel
 * @param betResults  per-bet detail
 * @param totalStaked everything wagered on this spin
 * @param totalPayout everything returned, stakes on winning bets included
 * @param net         payout minus stake; negative when the spin lost overall
 */
public record RouletteSpinResult(
        int pocket,
        PocketColor color,
        int wheelIndex,
        List<RouletteBetResult> betResults,
        BigDecimal totalStaked,
        BigDecimal totalPayout,
        BigDecimal net) {
}
