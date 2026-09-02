package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.GameType;
import com.casino.domain.LedgerEntryType;
import com.casino.game.common.Money;
import com.casino.game.slots.SlotMachine;
import com.casino.game.slots.SlotPayline;
import com.casino.game.slots.SpinResult;
import com.casino.security.CasinoPrincipal;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a slot spin end to end: take the stake, spin, pay any win.
 *
 * <p>The whole round is one transaction, so a failure part-way cannot leave a player charged for
 * a spin they were never paid for.
 *
 * <p>Slots deliberately do not go through the admin-managed table limits. A machine is not a
 * table game: the player dials in whatever denomination they like, down to a cent, and buys a
 * fixed number of credits. The only bounds are that the stake is a real positive amount and that
 * one spin cannot commit more than the configured ceiling.
 */
@Service
public class SlotsService {

    private final SlotMachine machine;
    private final WalletService wallet;
    private final List<Integer> creditOptions;
    private final BigDecimal maxTotalBet;

    public SlotsService(SlotMachine machine, WalletService wallet, CasinoProperties properties) {
        this.machine = machine;
        this.wallet = wallet;
        this.creditOptions = List.copyOf(properties.slots().creditOptions());
        this.maxTotalBet = Money.of(properties.slots().maxTotalBet());

        for (int option : creditOptions) {
            if (option < 1 || option > SlotPayline.count()) {
                throw new IllegalStateException(
                        "credit option " + option + " is not between 1 and " + SlotPayline.count());
            }
        }
    }

    @Transactional
    public SlotsRound spin(CasinoPrincipal principal, BigDecimal requestedBet, int requestedCredits) {
        int credits = validateCredits(requestedCredits);
        BigDecimal betPerLine = validateBet(requestedBet);
        BigDecimal totalStaked = Money.scaled(betPerLine.multiply(BigDecimal.valueOf(credits)));
        if (totalStaked.compareTo(maxTotalBet) > 0) {
            throw CasinoException.badRequest("Above " + maxTotalBet + " maximum.");
        }

        String roundId = UUID.randomUUID().toString();
        wallet.debit(principal, totalStaked, GameType.SLOTS, roundId,
                credits == 1 ? "Slots spin" : "Slots spin on " + credits + " lines");

        SpinResult result = machine.spin(betPerLine, credits);
        BigDecimal balance = wallet.credit(principal, result.payout(), LedgerEntryType.PAYOUT,
                GameType.SLOTS, roundId, result.describe());

        return new SlotsRound(roundId, result, balance);
    }

    /**
     * A machine has no minimum beyond being a real amount of money: a cent is a legitimate
     * denomination, so the only floor is what two decimal places can express.
     */
    private BigDecimal validateBet(BigDecimal amount) {
        if (amount == null) {
            throw CasinoException.badRequest("Bet amount required.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Two decimal places max.");
        }
        BigDecimal stake = Money.scaled(amount);
        if (!Money.isPositive(stake)) {
            throw CasinoException.badRequest("Bet must be positive.");
        }
        return stake;
    }

    /** Credits come from the buttons on the cabinet, not from a free-form field. */
    private int validateCredits(int credits) {
        if (!creditOptions.contains(credits)) {
            throw CasinoException.badRequest("Credits must be " + readableOptions() + ".");
        }
        return credits;
    }

    private String readableOptions() {
        if (creditOptions.size() == 1) {
            return String.valueOf(creditOptions.get(0));
        }
        List<String> text = creditOptions.stream().map(String::valueOf).toList();
        return String.join(", ", text.subList(0, text.size() - 1))
                + " or " + text.get(text.size() - 1);
    }

    public List<Integer> creditOptions() {
        return creditOptions;
    }

    public BigDecimal maxTotalBet() {
        return maxTotalBet;
    }

    /** A completed spin plus the balance it left behind. */
    public record SlotsRound(String roundId, SpinResult result, BigDecimal balance) {
    }
}
