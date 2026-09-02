package com.casino.web.dto;

import com.casino.game.slots.LineResult;
import com.casino.service.SlotsService;
import java.math.BigDecimal;
import java.util.List;

/**
 * The result of a spin.
 *
 * <p>The whole window is returned, not only the lines that were bought. A player who lit one
 * line should still see the diagonal they missed, which is what the glass on a real machine
 * shows them; it costs nothing to disclose, because the outcome is already decided.
 *
 * @param stops       stop index per reel, so the UI can animate to the position that landed
 * @param window      three symbols per reel, top row first, in reel order
 * @param lines       one entry per line the player lit
 * @param betPerLine  the stake on each lit line
 * @param credits     lines lit
 * @param totalStaked bet per line times credits
 */
public record SlotsResponse(
        String roundId,
        List<Integer> stops,
        List<List<String>> window,
        List<LineView> lines,
        int totalMultiplier,
        String combination,
        boolean win,
        BigDecimal betPerLine,
        int credits,
        BigDecimal totalStaked,
        BigDecimal payout,
        BigDecimal net,
        BigDecimal balance) {

    /**
     * One lit line.
     *
     * @param rows  the row this line reads on each reel, top row 0, so the UI can draw it
     *              without knowing the payline names
     */
    public record LineView(
            String payline,
            String name,
            List<Integer> rows,
            List<String> symbols,
            int multiplier,
            BigDecimal payout,
            String combination,
            boolean win) {

        static LineView from(LineResult line) {
            return new LineView(
                    line.payline().name(),
                    line.payline().displayName(),
                    line.payline().rows(),
                    line.symbols().stream().map(Enum::name).toList(),
                    line.multiplier(),
                    line.payout(),
                    line.combination(),
                    line.isWin());
        }
    }

    public static SlotsResponse from(SlotsService.SlotsRound round) {
        var result = round.result();
        return new SlotsResponse(
                round.roundId(),
                result.stops(),
                result.window().stream()
                        .map(reel -> reel.stream().map(s -> ((Enum<?>) s).name()).toList())
                        .toList(),
                result.lines().stream().map(LineView::from).toList(),
                result.totalMultiplier(),
                result.describe(),
                result.isWin(),
                result.betPerLine(),
                result.credits(),
                result.totalStaked(),
                result.payout(),
                result.net(),
                round.balance());
    }
}
