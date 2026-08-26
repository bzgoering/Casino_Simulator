package com.casino.game.roulette;

import com.casino.game.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Spins the wheel and settles every bet on the layout.
 *
 * <p>Stateless: bets arrive already validated as {@link RouletteBet} instances, the wheel is
 * spun once, and each bet is scored independently against the winning pocket.
 */
public final class RouletteTable {

    private final RouletteWheel wheel;

    public RouletteTable(RouletteWheel wheel) {
        this.wheel = wheel;
    }

    public RouletteSpinResult spin(List<RouletteBet> bets) {
        if (bets == null || bets.isEmpty()) {
            throw new IllegalArgumentException("At least one bet is required");
        }
        int pocket = wheel.spin();

        List<RouletteBetResult> results = new ArrayList<>(bets.size());
        BigDecimal staked = BigDecimal.ZERO;
        BigDecimal payout = BigDecimal.ZERO;

        for (RouletteBet bet : bets) {
            boolean won = bet.wins(pocket);
            BigDecimal betPayout = bet.payoutFor(pocket);
            results.add(new RouletteBetResult(bet.type(), bet.selection(), bet.amount(), won, betPayout));
            staked = staked.add(bet.amount());
            payout = payout.add(betPayout);
        }

        BigDecimal totalStaked = Money.scaled(staked);
        BigDecimal totalPayout = Money.scaled(payout);
        return new RouletteSpinResult(
                pocket,
                RouletteWheel.colorOf(pocket),
                RouletteWheel.wheelIndexOf(pocket),
                List.copyOf(results),
                totalStaked,
                totalPayout,
                Money.scaled(totalPayout.subtract(totalStaked)));
    }
}
