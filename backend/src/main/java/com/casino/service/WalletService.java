package com.casino.service;

import com.casino.domain.GameType;
import com.casino.domain.LedgerEntry;
import com.casino.domain.LedgerEntryType;
import com.casino.domain.UserAccount;
import com.casino.game.common.Money;
import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place balances change.
 *
 * <p>Guests and registered accounts are stored very differently, but every caller goes through
 * this one type, so the rules that matter hold for both: a balance can never go negative, and no
 * amount is ever taken from the request body as gospel.
 *
 * <p>For registered accounts the row is loaded with {@code SELECT ... FOR UPDATE} and the write
 * happens inside the caller's transaction. Concurrent bets on one account therefore serialise at
 * the database instead of racing, which is what stops a player from spending the same balance
 * twice by firing simultaneous requests.
 */
@Service
public class WalletService {

    private final UserAccountRepository users;
    private final LedgerEntryRepository ledger;
    private final GuestSessionService guests;

    public WalletService(UserAccountRepository users, LedgerEntryRepository ledger, GuestSessionService guests) {
        this.users = users;
        this.ledger = ledger;
        this.guests = guests;
    }

    /** Current authoritative balance for the caller. */
    @Transactional(readOnly = true)
    public BigDecimal balanceOf(CasinoPrincipal principal) {
        if (principal.isGuest()) {
            return guests.require(principal.subject()).balance();
        }
        return requireAccount(principal).getBalance();
    }

    /**
     * Removes a stake from the balance.
     *
     * @throws CasinoException with 422 when the caller cannot cover it
     * @return the balance after the debit
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BigDecimal debit(CasinoPrincipal principal, BigDecimal amount, GameType game,
                            String roundId, String detail) {
        BigDecimal stake = requirePositive(amount);

        if (principal.isGuest()) {
            GuestSession session = guests.require(principal.subject());
            BigDecimal balance = session.balance();
            if (balance.compareTo(stake) < 0) {
                throw insufficientFunds();
            }
            BigDecimal updated = Money.scaled(balance.subtract(stake));
            guests.updateBalance(session, updated);
            return updated;
        }

        UserAccount account = lockAccount(principal);
        BigDecimal balance = account.getBalance();
        if (balance.compareTo(stake) < 0) {
            throw insufficientFunds();
        }
        BigDecimal updated = Money.scaled(balance.subtract(stake));
        account.setBalance(updated);
        users.save(account);
        ledger.save(new LedgerEntry(account.getId(), LedgerEntryType.BET, game,
                Money.scaled(stake.negate()), updated, roundId, detail));
        return updated;
    }

    /**
     * Adds winnings or a credit to the balance. A zero amount is accepted and simply records
     * nothing, which keeps losing-round settlement branch-free at the call site.
     *
     * @return the balance after the credit
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public BigDecimal credit(CasinoPrincipal principal, BigDecimal amount, LedgerEntryType type,
                             GameType game, String roundId, String detail) {
        BigDecimal payout = Money.scaled(amount);
        if (payout.signum() < 0) {
            throw CasinoException.badRequest("Credit cannot be negative.");
        }

        if (principal.isGuest()) {
            GuestSession session = guests.require(principal.subject());
            if (payout.signum() == 0) {
                return session.balance();
            }
            BigDecimal updated = Money.scaled(session.balance().add(payout));
            guests.updateBalance(session, updated);
            return updated;
        }

        UserAccount account = lockAccount(principal);
        if (payout.signum() == 0) {
            return account.getBalance();
        }
        BigDecimal updated = Money.scaled(account.getBalance().add(payout));
        account.setBalance(updated);
        users.save(account);
        ledger.save(new LedgerEntry(account.getId(), type, game, payout, updated, roundId, detail));
        return updated;
    }

    /** Credits a registered account directly, used by the admin path where the target is not the caller. */
    @Transactional
    public BigDecimal creditAccount(UserAccount account, BigDecimal amount, LedgerEntryType type,
                                    GameType game, String roundId, String detail) {
        BigDecimal credit = Money.scaled(amount);
        BigDecimal updated = Money.scaled(account.getBalance().add(credit));
        account.setBalance(updated);
        users.save(account);
        ledger.save(new LedgerEntry(account.getId(), type, game, credit, updated, roundId, detail));
        return updated;
    }

    /** Credits a guest session directly, used by the admin path. */
    public BigDecimal creditGuest(GuestSession session, BigDecimal amount) {
        BigDecimal updated = Money.scaled(session.balance().add(Money.scaled(amount)));
        guests.updateBalance(session, updated);
        return updated;
    }

    private UserAccount lockAccount(CasinoPrincipal principal) {
        UserAccount account = requireAccount(principal);
        return users.findByIdForUpdate(account.getId()).orElseThrow(
                () -> CasinoException.notFound("Account not found."));
    }

    private UserAccount requireAccount(CasinoPrincipal principal) {
        return users.findByUid(principal.subject())
                .orElseThrow(() -> new CasinoException(HttpStatus.UNAUTHORIZED,
                        "Your session is no longer valid. Please sign in again."));
    }

    private static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal scaled = Money.scaled(amount);
        if (!Money.isPositive(scaled)) {
            throw CasinoException.badRequest("Bet must be positive.");
        }
        return scaled;
    }

    /**
     * Deliberately says only that the balance is short. The figures are already on screen, and
     * the player is told what they can do about it, not audited back at themselves.
     */
    private static CasinoException insufficientFunds() {
        return new CasinoException(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough money.");
    }
}
