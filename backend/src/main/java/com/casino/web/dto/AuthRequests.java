package com.casino.web.dto;

import com.casino.service.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the auth endpoints.
 *
 * <p>These check shape only: present, and not absurdly long. Every rule about what makes an
 * acceptable password lives in {@code PasswordPolicy} and nowhere else. Restating the length
 * here would not merely be duplication: an annotation fires before the service does, so the
 * copy here would be the one the player actually saw, and the two would answer the same mistake
 * differently on different endpoints.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record SignUpRequest(
            @NotBlank(message = "Username is required.")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$",
                    message = "Username must be 3-32 characters using letters, numbers or underscore.")
            String username,

            @NotBlank(message = "Password is required.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required.")
            @Size(max = 32, message = "Username is too long.")
            String username,

            // Not the password policy: signing in does not re-apply it, and an existing
            // account may predate a change to it. This is only a bound on what is worth
            // handing to BCrypt from an unauthenticated endpoint.
            @NotBlank(message = "Password is required.")
            @Size(max = PasswordPolicy.MAX_BYTES, message = "Password is too long.")
            String password) {
    }
}
