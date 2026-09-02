package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.GameType;
import com.casino.domain.LedgerEntry;
import com.casino.domain.LedgerEntryType;
import com.casino.domain.Role;
import com.casino.domain.UserAccount;
import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.security.JwtService;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, sign-in and guest entry.
 *
 * <p>Security decisions worth calling out:
 * <ul>
 *   <li>Passwords are BCrypt hashed. BCrypt silently ignores anything past 72 bytes, so longer
 *       passwords are rejected outright rather than being quietly truncated to a weaker secret.
 *   <li>A failed sign-in returns exactly the same message whether the username exists or not, and
 *       a hash is verified even for unknown users so the response time does not reveal which.
 *   <li>Repeated failures lock the account for a cooling-off period.
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    /** A dummy hash to verify against when the username is unknown, to equalise timing. */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserAccountRepository users;
    private final LedgerEntryRepository ledger;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GuestSessionService guests;
    private final PasswordPolicy passwordPolicy;
    private final CasinoProperties properties;

    public AuthService(UserAccountRepository users, LedgerEntryRepository ledger,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       GuestSessionService guests, PasswordPolicy passwordPolicy,
                       CasinoProperties properties) {
        this.users = users;
        this.ledger = ledger;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.guests = guests;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
    }

    /** Registers a new player, grants the opening balance, and signs them in. */
    @Transactional
    public AuthResult signUp(String rawUsername, String rawPassword) {
        String username = validateUsername(rawUsername);
        passwordPolicy.validate(rawPassword, username);

        if (users.existsByUsernameIgnoreCase(username)) {
            throw CasinoException.conflict("That username is already taken.");
        }

        BigDecimal opening = new BigDecimal(Role.PLAYER.startingBalance());
        UserAccount account = new UserAccount(
                username, passwordEncoder.encode(rawPassword), Role.PLAYER, opening);
        users.save(account);

        ledger.save(new LedgerEntry(account.getId(), LedgerEntryType.SIGNUP_GRANT, GameType.ACCOUNT,
                opening, opening, null, "Welcome balance"));

        log.info("Registered new player uid={}", account.getUid());
        return issueFor(account);
    }

    /** Signs an existing user in. */
    @Transactional
    public AuthResult signIn(String rawUsername, String rawPassword) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        var maybeAccount = users.findByUsernameIgnoreCase(username);

        if (maybeAccount.isEmpty()) {
            // Spend the same work as a real verification so timing does not disclose existence.
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, DUMMY_HASH);
            throw invalidCredentials();
        }

        UserAccount account = maybeAccount.get();
        if (account.isLocked()) {
            throw new CasinoException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in a few minutes.");
        }
        if (!account.isEnabled()) {
            throw CasinoException.forbidden("This account has been disabled.");
        }
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, account.getPasswordHash())) {
            account.recordFailedLogin(
                    properties.security().maxFailedLogins(), properties.security().lockDuration());
            users.save(account);
            throw invalidCredentials();
        }

        account.recordSuccessfulLogin();
        users.save(account);
        return issueFor(account);
    }

    /** Opens an anonymous session with the standard play-money balance. Nothing is persisted. */
    public AuthResult signInAsGuest() {
        GuestSession session = guests.create();
        CasinoPrincipal principal = new CasinoPrincipal(session.id(), "guest", Role.GUEST);
        return new AuthResult(jwtService.issue(principal), principal, session.balance());
    }

    private AuthResult issueFor(UserAccount account) {
        CasinoPrincipal principal =
                new CasinoPrincipal(account.getUid(), account.getUsername(), account.getRole());
        return new AuthResult(jwtService.issue(principal), principal, account.getBalance());
    }

    private static CasinoException invalidCredentials() {
        return new CasinoException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    private static String validateUsername(String raw) {
        String username = raw == null ? "" : raw.trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw CasinoException.badRequest(
                    "Username must be 3-32 characters using letters, numbers or underscore.");
        }
        return username;
    }

    /** A signed-in caller: their token, who they are, and their balance. */
    public record AuthResult(String token, CasinoPrincipal principal, BigDecimal balance) {
    }
}
