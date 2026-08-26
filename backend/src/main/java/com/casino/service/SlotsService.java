package com.casino.service;

import com.casino.domain.GameType;
import com.casino.domain.LedgerEntryType;
import com.casino.game.slots.SlotMachine;
import com.casino.game.slots.SpinResult;
import com.casino.security.CasinoPrincipal;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a slot spin end to end: take the stake, spin, pay any win.
 *
 * <p>The whole round is one transaction, so a failure part-way cannot leave a player charged for
 * a spin they were never paid for.
 */
@Service
public class SlotsService {

    private final SlotMachine machine;
    private final WalletService wallet;
    private final BetValidator betValidator;

    public SlotsService(SlotMachine machine, WalletService wallet, BetValidator betValidator) {
        this.machine = machine;
        this.wallet = wallet;
        this.betValidator = betValidator;
    }

    @Transactional
    public SlotsRound spin(CasinoPrincipal principal, BigDecimal requestedBet) {
        BigDecimal stake = betValidator.validate(requestedBet);
        String roundId = UUID.randomUUID().toString();

        wallet.debit(principal, stake, GameType.SLOTS, roundId, "Slots spin");
        SpinResult result = machine.spin(stake);
        BigDecimal balance = wallet.credit(principal, result.payout(), LedgerEntryType.PAYOUT,
                GameType.SLOTS, roundId, result.combination());

        return new SlotsRound(roundId, result, balance);
    }

    /** A completed spin plus the balance it left behind. */
    public record SlotsRound(String roundId, SpinResult result, BigDecimal balance) {
    }
}
