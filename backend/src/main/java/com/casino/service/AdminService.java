package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.AdminAuditEntry;
import com.casino.domain.GameType;
import com.casino.domain.LedgerEntryType;
import com.casino.domain.UserAccount;
import com.casino.game.common.Money;
import com.casino.repository.AdminAuditRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Privileged operations.
 *
 * <p>Minting balance is the most abusable thing anyone can do here, so it is wrapped in several
 * controls: the method is role-gated with {@link PreAuthorize} in addition to the URL rules, a
 * single credit is capped by configuration, and every call is written to an immutable audit
 * table with the actor, the target and the caller's address.
 *
 * <p>A target is resolved as a registered account UID first, then as a guest session id. Guests
 * can be credited even though nothing about them is stored, because their session is live in
 * memory for as long as they are playing.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserAccountRepository users;
    private final AdminAuditRepository audit;
    private final WalletService wallet;
    private final GuestSessionService guests;
    private final BetValidator betValidator;
    private final BigDecimal maxCredit;

    public AdminService(UserAccountRepository users, AdminAuditRepository audit, WalletService wallet,
                        GuestSessionService guests, BetValidator betValidator,
                        CasinoProperties properties) {
        this.users = users;
        this.audit = audit;
        this.wallet = wallet;
        this.guests = guests;
        this.betValidator = betValidator;
        this.maxCredit = Money.of(properties.limits().maxAdminCredit());
    }

    /**
     * Adds balance to the caller's own account.
     *
     * @return the new balance
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CreditResult creditSelf(CasinoPrincipal actor, BigDecimal amount, String sourceIp) {
        return creditByUid(actor, actor.subject(), amount, sourceIp);
    }

    /**
     * Adds balance to any account or live guest session, identified by its UID.
     *
     * @param targetRef a registered account UID, or a guest session id
     * @return the target's new balance
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CreditResult creditByUid(CasinoPrincipal actor, String targetRef, BigDecimal amount, String sourceIp) {
        BigDecimal credit = validateAmount(amount);
        String ref = targetRef == null ? "" : targetRef.trim();
        if (ref.isEmpty()) {
            throw CasinoException.badRequest("Target UID required.");
        }

        Optional<UserAccount> account = users.findByUid(ref);
        if (account.isPresent()) {
            UserAccount target = account.get();
            BigDecimal balance = wallet.creditAccount(target, credit, LedgerEntryType.ADMIN_CREDIT,
                    GameType.ACCOUNT, null, "Credited by admin " + actor.username());
            recordAudit(actor, ref, "PLAYER", credit, sourceIp);
            log.info("Admin {} credited {} to account uid={}", actor.username(), credit, ref);
            return new CreditResult(ref, "PLAYER", target.getUsername(), credit, balance);
        }

        Optional<GuestSession> guest = guests.find(ref);
        if (guest.isPresent()) {
            BigDecimal balance = wallet.creditGuest(guest.get(), credit);
            recordAudit(actor, ref, "GUEST", credit, sourceIp);
            log.info("Admin {} credited {} to guest session", actor.username(), credit);
            return new CreditResult(ref, "GUEST", "guest", credit, balance);
        }

        throw CasinoException.notFound("No such UID.");
    }

    /**
     * Changes one game's betting limits.
     *
     * <p>Audited like a credit: a limit change is not a movement of money, but it decides how
     * much money every subsequent bet on that game may move, so it belongs in the same trail.
     * The ceiling on the maximum stays in configuration and is enforced by {@link BetValidator}.
     *
     * @return the limits now in force, for every game
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public LimitsResult updateLimits(CasinoPrincipal actor, GameType game, BigDecimal minBet,
                                     BigDecimal maxBet, String sourceIp) {
        BetValidator.Limits updated = betValidator.updateLimits(game, minBet, maxBet, actor.username());

        audit.save(new AdminAuditEntry(actor.subject(), actor.username(), "SET_GAME_LIMITS",
                game + " " + updated.min() + "-" + updated.max(), "TABLE", null, sourceIp));
        log.info("Admin {} set {} limits to {} - {}", actor.username(), game,
                updated.min(), updated.max());
        return currentLimits();
    }

    /** Every game's limits and the ceiling an admin may not exceed. */
    public LimitsResult currentLimits() {
        Map<String, BetValidator.Limits> byGame = new LinkedHashMap<>();
        betValidator.all().forEach((game, limits) -> byGame.put(game.name(), limits));
        return new LimitsResult(byGame, betValidator.maxConfigurableBet());
    }

    /** The limits in force, keyed by game name, for the admin console. */
    public record LimitsResult(Map<String, BetValidator.Limits> games, BigDecimal maxConfigurableBet) {
    }

    private void recordAudit(CasinoPrincipal actor, String targetRef, String targetKind,
                             BigDecimal amount, String sourceIp) {
        audit.save(new AdminAuditEntry(actor.subject(), actor.username(), "CREDIT_BALANCE",
                targetRef, targetKind, amount, sourceIp));
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || !Money.isPositive(amount)) {
            throw CasinoException.badRequest("Credit must be positive.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw CasinoException.badRequest("Two decimal places max.");
        }
        BigDecimal credit = Money.scaled(amount);
        if (credit.compareTo(maxCredit) > 0) {
            throw CasinoException.badRequest("Credit above " + maxCredit + ".");
        }
        return credit;
    }

    /** The result of a credit, for the admin console. */
    public record CreditResult(String targetRef, String targetKind, String targetUsername,
                               BigDecimal amountCredited, BigDecimal newBalance) {
    }
}
