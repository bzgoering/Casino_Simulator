package com.casino.web.dto;

import com.casino.game.roulette.RouletteWheel;
import com.casino.service.RouletteService;
import java.math.BigDecimal;
import java.util.List;

/**
 * The result of a spin across every chip on the layout.
 *
 * @param pocket     the winning pocket as it is written on the cloth, so the double zero
 *                   reaches the browser as "00" rather than as the 37 it is held as
 * @param wheelIndex the winning pocket's position in the physical wheel order, so the browser can
 *                   animate the wheel to the pocket the server already chose
 */
public record RouletteResponse(
        String roundId,
        String pocket,
        String color,
        int wheelIndex,
        List<BetResultView> bets,
        BigDecimal totalStaked,
        BigDecimal totalPayout,
        BigDecimal net,
        BigDecimal balance) {

    public record BetResultView(
            String type,
            String selection,
            BigDecimal amount,
            boolean won,
            BigDecimal payout) {
    }

    public static RouletteResponse from(RouletteService.RouletteRound round) {
        var result = round.result();
        List<BetResultView> bets = result.betResults().stream()
                .map(b -> new BetResultView(
                        b.type().name(), b.selection(), b.amount(), b.won(), b.payout()))
                .toList();

        return new RouletteResponse(
                round.roundId(),
                RouletteWheel.label(result.pocket()),
                result.color().name(),
                result.wheelIndex(),
                bets,
                result.totalStaked(),
                result.totalPayout(),
                result.net(),
                round.balance());
    }
}
