package com.casino.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request bodies for the auth endpoints.
 *
 * <p>These bounds are a first line of defence that rejects obvious junk before it reaches the
 * service. The authoritative rules still live in {@code AuthService}: validation annotations are
 * easy to forget on a new endpoint, so they are never the only check.
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
            @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Username is required.")
            @Size(max = 32, message = "Username is too long.")
            String username,

            @NotBlank(message = "Password is required.")
            @Size(max = 72, message = "Password is too long.")
            String password) {
    }
}
