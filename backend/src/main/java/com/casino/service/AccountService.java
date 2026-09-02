package com.casino.service;

import com.casino.domain.Role;
import com.casino.domain.UserAccount;
import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.web.error.CasinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a signed-in caller may do to their own account: change its password, or close it.
 *
 * <p>Both operations re-verify the current password. The bearer token alone is not enough: a
 * token left behind on a shared machine should not be able to lock the owner out or destroy
 * their history, and re-authenticating is the standard control for exactly that.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserAccountRepository users;
    private final LedgerEntryRepository ledger;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final GuestSessionService guests;
    private final BlackjackService blackjack;

    public AccountService(UserAccountRepository users, LedgerEntryRepository ledger,
                          PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                          GuestSessionService guests, BlackjackService blackjack) {
        this.users = users;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.guests = guests;
        this.blackjack = blackjack;
    }

    /**
     * Replaces the caller's password.
     *
     * <p>Existing tokens stay valid: this build has no revocation list, so a change locks nobody
     * out of a session already in progress. That is a known limit, recorded in the README, not an
     * oversight here.
     */
    @Transactional
    public void changePassword(CasinoPrincipal principal, String currentPassword, String newPassword) {
        if (principal.isGuest()) {
            throw CasinoException.badRequest("Guests have no password.");
        }
        UserAccount account = requireAccount(principal);

        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword,
                account.getPasswordHash())) {
            throw wrongPassword("Current password is wrong.");
        }
        passwordPolicy.validate(newPassword, account.getUsername());
        if (passwordEncoder.matches(newPassword, account.getPasswordHash())) {
            throw CasinoException.badRequest("New password must differ.");
        }

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(account);
        log.info("Password changed for uid={}", account.getUid());
    }

    /**
     * Closes the caller's account for good.
     *
     * <p>For a guest there is nothing to delete: the session is dropped and, since guests were
     * never written to the database, that is genuinely the end of them. For a registered account
     * the ledger is removed explicitly and then the row itself. The admin audit trail
     * deliberately survives: it records what an administrator did, and must outlive the account
     * it was done to.
     *
     * @return what was actually closed, for the message shown to the player
     */
    @Transactional
    public DeletionResult deleteAccount(CasinoPrincipal principal, String password) {
        // Whatever happens, the seat at the blackjack table goes with the account.
        blackjack.closeTable(principal.subject());

        if (principal.isGuest()) {
            guests.end(principal.subject());
            log.info("Guest session ended at the player's request");
            return new DeletionResult("GUEST", "guest");
        }

        UserAccount account = requireAccount(principal);
        if (!passwordEncoder.matches(password == null ? "" : password, account.getPasswordHash())) {
            throw wrongPassword("Password is wrong.");
        }
        // Refusing the last admin is not paternalism about the account: it is the only thing
        // standing between a mistyped click and a deployment nobody can administer again.
        if (account.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw CasinoException.conflict("The last admin cannot be deleted.");
        }

        String username = account.getUsername();
        String uid = account.getUid();
        Role role = account.getRole();

        long entries = ledger.deleteByUserId(account.getId());
        users.delete(account);
        log.info("Account deleted at the owner's request: uid={}, {} ledger entries removed",
                uid, entries);
        return new DeletionResult(role.name(), username);
    }

    /**
     * Deliberately not a 401.
     *
     * <p>The caller's session is perfectly valid; it is the password they typed into the form
     * that is wrong. Answering 401 would say the token had failed, and the browser treats that
     * as a dead session and signs the user out -- so mistyping your own password once would log
     * you out instead of telling you to try again.
     */
    private static CasinoException wrongPassword(String message) {
        return new CasinoException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private UserAccount requireAccount(CasinoPrincipal principal) {
        return users.findByUid(principal.subject())
                .orElseThrow(() -> new CasinoException(HttpStatus.UNAUTHORIZED,
                        "Your session is no longer valid. Please sign in again."));
    }

    /** What was closed. */
    public record DeletionResult(String kind, String username) {
    }
}
