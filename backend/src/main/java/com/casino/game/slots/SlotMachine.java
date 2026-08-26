package com.casino.game.slots;

import com.casino.game.common.Money;
import com.casino.game.common.RandomSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The three-reel, single-payline slot machine.
 *
 * <p>Each reel stops independently and uniformly on one of the 32 strip positions, drawn from the
 * injected {@link RandomSource}. There is no "near miss" weighting and no adjustment based on how
 * the player has been doing: every spin is independent, which is both the honest model and what a
 * regulated machine is required to do.
 *
 * <p>Stateless and thread-safe.
 */
public final class SlotMachine {

    private final RandomSource random;

    public SlotMachine(RandomSource random) {
        this.random = random;
    }

    public SpinResult spin(BigDecimal bet) {
        BigDecimal stake = Money.scaled(bet);

        List<Integer> stops = new ArrayList<>(SlotPaytable.REEL_COUNT);
        List<SlotSymbol> symbols = new ArrayList<>(SlotPaytable.REEL_COUNT);
        for (int reel = 0; reel < SlotPaytable.REEL_COUNT; reel++) {
            int stop = random.nextInt(SlotPaytable.REEL_STRIP.size());
            stops.add(stop);
            symbols.add(SlotPaytable.REEL_STRIP.get(stop));
        }

        int multiplier = SlotPaytable.multiplierFor(symbols.get(0), symbols.get(1), symbols.get(2));
        String combination = SlotPaytable.describe(symbols.get(0), symbols.get(1), symbols.get(2));

        BigDecimal payout = multiplier == 0
                ? Money.ZERO
                : Money.scaled(stake.multiply(BigDecimal.valueOf(multiplier)));
        BigDecimal net = Money.scaled(payout.subtract(stake));

        return new SpinResult(List.copyOf(stops), List.copyOf(symbols), multiplier, payout, net, combination);
    }
}
