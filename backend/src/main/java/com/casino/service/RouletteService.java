package com.casino.service;

import com.casino.domain.GameType;
import com.casino.domain.LedgerEntryType;
import com.casino.game.common.Money;
import com.casino.game.roulette.RouletteBet;
import com.casino.game.roulette.RouletteSpinResult;
import com.casino.game.roulette.RouletteTable;
import com.casino.security.CasinoPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a roulette spin: validate every chip, take the total, spin once, pay the winners.
 *
 * <p>The wheel is spun exactly once for the whole layout, after all stakes are committed. That
 * ordering matters: taking the money first means a mid-round failure can never pay out on a spin
 * that was not paid for.
 */
@Service
public class RouletteService {

    private final RouletteTable table;
    private final WalletService wallet;
    private final BetValidator betValidator;

    public RouletteService(RouletteTable table, WalletService wallet, BetValidator betValidator) {
        this.table = table;
        this.wallet = wallet;
        this.betValidator = betValidator;
    }

    @Transactional
    public RouletteRound spin(CasinoPrincipal principal, List<RouletteBet> bets) {
        betValidator.validateRouletteBetCount(bets.size());
        bets.forEach(bet -> betValidator.validate(bet.amount()));

        BigDecimal total = Money.scaled(bets.stream()
                .map(RouletteBet::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        String roundId = UUID.randomUUID().toString();
        wallet.debit(principal, total, GameType.ROULETTE, roundId, bets.size() + " bet(s)");

        RouletteSpinResult result = table.spin(bets);
        BigDecimal balance = wallet.credit(principal, result.totalPayout(), LedgerEntryType.PAYOUT,
                GameType.ROULETTE, roundId, "Pocket " + result.pocket() + " " + result.color());

        return new RouletteRound(roundId, result, balance);
    }

    public record RouletteRound(String roundId, RouletteSpinResult result, BigDecimal balance) {
    }
}
