package com.casino.web.dto;

import com.casino.service.AdminService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Request and response bodies for the admin console. */
public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * Credit a balance.
     *
     * @param targetUid a player account UID, or a live guest session id. Omit to credit yourself.
     */
    public record CreditRequest(
            @Size(max = 64, message = "UID is too long.")
            String targetUid,

            @NotNull(message = "An amount is required.")
            @DecimalMin(value = "0.01", message = "Credit must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Credit can have at most 2 decimal places.")
            BigDecimal amount) {
    }

    /** Credit yourself, for the admin's own balance. */
    public record SelfCreditRequest(
            @NotNull(message = "An amount is required.")
            @DecimalMin(value = "0.01", message = "Credit must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Credit can have at most 2 decimal places.")
            BigDecimal amount) {
    }

    public record CreditResponse(
            String targetUid,
            String targetKind,
            String targetUsername,
            BigDecimal amountCredited,
            BigDecimal newBalance) {

        public static CreditResponse from(AdminService.CreditResult result) {
            return new CreditResponse(
                    result.targetRef(),
                    result.targetKind(),
                    result.targetUsername(),
                    result.amountCredited(),
                    result.newBalance());
        }
    }

    /** One line of the privileged-action audit log. */
    public record AuditEntryView(
            String actorUsername,
            String action,
            String targetRef,
            String targetKind,
            BigDecimal amount,
            String sourceIp,
            java.time.Instant at) {
    }
}
