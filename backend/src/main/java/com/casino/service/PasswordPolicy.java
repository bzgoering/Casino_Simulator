package com.casino.service;

import com.casino.web.error.CasinoException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The rules a password has to satisfy, in one place.
 *
 * <p>Shared by sign-up and by a later password change. Two copies of these rules would drift, and
 * the copy that drifted would be the one letting a weaker password in through the side door.
 */
@Component
public class PasswordPolicy {

    /** Public so annotations and callers can state the bound without restating the number. */
    public static final int MIN_LENGTH = 10;

    /** BCrypt silently ignores anything past 72 bytes, so a longer password is refused outright. */
    public static final int MAX_BYTES = 72;

    private static final Set<String> BANNED = Set.of(
            "password12", "password123", "1234567890", "qwertyuiop", "letmein123",
            "iloveyou12", "welcome123", "admin12345", "casino1234", "blackjack1");

    /**
     * @param username the account the password is for; a password containing it is refused
     * @throws CasinoException with 400 when the password is not acceptable
     */
    public void validate(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw CasinoException.badRequest(
                    "Password must be at least " + MIN_LENGTH + " characters.");
        }
        // BCrypt ignores bytes past 72; a longer password would be silently weakened.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw CasinoException.badRequest("Password must be at most 72 bytes.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (BANNED.contains(lower)) {
            throw CasinoException.badRequest("That password is too common. Please choose another.");
        }
        if (username != null && !username.isBlank()
                && lower.contains(username.toLowerCase(Locale.ROOT))) {
            throw CasinoException.badRequest("Password must not contain your username.");
        }
    }

    public int minLength() {
        return MIN_LENGTH;
    }
}
