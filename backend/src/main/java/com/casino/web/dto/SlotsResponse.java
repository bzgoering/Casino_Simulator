package com.casino.web.dto;

import com.casino.service.SlotsService;
import java.math.BigDecimal;
import java.util.List;

/**
 * The result of a spin.
 *
 * @param stops       stop index per reel, so the UI can animate to the position that actually won
 * @param symbols     the symbol on the payline for each reel
 * @param multiplier  payout multiplier on the line bet; 0 for a loss
 * @param combination human-readable name of the win
 */
public record SlotsResponse(
        String roundId,
        List<Integer> stops,
        List<String> symbols,
        int multiplier,
        String combination,
        boolean win,
        BigDecimal bet,
        BigDecimal payout,
        BigDecimal net,
        BigDecimal balance) {

    public static SlotsResponse from(SlotsService.SlotsRound round, BigDecimal bet) {
        var result = round.result();
        return new SlotsResponse(
                round.roundId(),
                result.stops(),
                result.symbols().stream().map(Enum::name).toList(),
                result.multiplier(),
                result.combination(),
                result.isWin(),
                bet,
                result.payout(),
                result.net(),
                round.balance());
    }
}
