package com.casino.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request bodies for the caller's own account.
 *
 * <p>Both carry the current password. These check shape only: every rule about what makes an
 * acceptable password lives in {@code PasswordPolicy}, so the same mistake gets the same answer
 * whether it is made at sign-up or here.
 */
public final class AccountRequests {

    private AccountRequests() {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Your current password is required.")
            String currentPassword,

            @NotBlank(message = "A new password is required.")
            String newPassword) {
    }

    /**
     * Close the caller's own account.
     *
     * @param password required for a registered account, ignored for a guest, who has none
     */
    public record DeleteAccountRequest(String password) {
    }
}
