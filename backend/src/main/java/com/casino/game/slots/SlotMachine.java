package com.casino.game.slots;

import com.casino.game.common.Money;
import com.casino.game.common.RandomSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The three-reel, three-row slot machine.
 *
 * <p>Each reel stops independently and uniformly on one of the 32 strip positions, drawn from the
 * injected {@link RandomSource}, and shows that stop with its neighbour either side. There is no
 * "near miss" weighting and no adjustment based on how the player has been doing: every spin is
 * independent, which is both the honest model and what a regulated machine is required to do.
 *
 * <p>Only the lines the player lit are scored. An unlit line that would have paid is still
 * reported in the window but pays nothing, exactly as on a real cabinet.
 *
 * <p>Stateless and thread-safe.
 */
public final class SlotMachine {

    private final RandomSource random;

    public SlotMachine(RandomSource random) {
        this.random = random;
    }

    /**
     * Spins with {@code credits} lines lit at {@code betPerLine} each.
     *
     * @param betPerLine the stake on every lit line; the total taken is this times the credits
     * @param credits    lines to light, from 1 to the number of paylines
     */
    public SpinResult spin(BigDecimal betPerLine, int credits) {
        BigDecimal lineStake = Money.scaled(betPerLine);
        List<SlotPayline> lit = SlotPayline.litBy(credits);

        List<Integer> stops = new ArrayList<>(SlotPaytable.REEL_COUNT);
        List<List<SlotSymbol>> window = new ArrayList<>(SlotPaytable.REEL_COUNT);
        for (int reel = 0; reel < SlotPaytable.REEL_COUNT; reel++) {
            int stop = random.nextInt(SlotPaytable.REEL_STRIP.size());
            stops.add(stop);
            window.add(SlotPaytable.windowAt(stop));
        }

        List<LineResult> lines = new ArrayList<>(lit.size());
        BigDecimal payout = Money.ZERO;
        for (SlotPayline payline : lit) {
            List<SlotSymbol> symbols = readLine(window, payline);
            int multiplier = SlotPaytable.multiplierFor(symbols);
            BigDecimal linePayout = multiplier == 0
                    ? Money.ZERO
                    : Money.scaled(lineStake.multiply(BigDecimal.valueOf(multiplier)));
            payout = payout.add(linePayout);
            lines.add(new LineResult(payline, symbols, multiplier, linePayout,
                    SlotPaytable.describe(symbols)));
        }

        BigDecimal totalStaked = Money.scaled(lineStake.multiply(BigDecimal.valueOf(credits)));
        payout = Money.scaled(payout);

        return new SpinResult(
                List.copyOf(stops),
                List.copyOf(window),
                List.copyOf(lines),
                lineStake,
                credits,
                totalStaked,
                payout,
                Money.scaled(payout.subtract(totalStaked)));
    }

    /** The symbols one line reads: its row on each reel, in reel order. */
    private static List<SlotSymbol> readLine(List<List<SlotSymbol>> window, SlotPayline payline) {
        List<SlotSymbol> symbols = new ArrayList<>(SlotPaytable.REEL_COUNT);
        for (int reel = 0; reel < SlotPaytable.REEL_COUNT; reel++) {
            symbols.add(window.get(reel).get(payline.rowOnReel(reel)));
        }
        return List.copyOf(symbols);
    }
}
