package com.casino.web.dto;

import com.casino.service.AdminService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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

    /**
     * Change one game's betting limits.
     *
     * <p>Both bounds are required: sending one and leaving the other implicit would let a
     * mis-typed minimum sit above an unchanged maximum.
     */
    public record LimitsRequest(
            @NotNull(message = "A game is required.")
            com.casino.domain.GameType game,

            @NotNull(message = "A minimum bet is required.")
            @DecimalMin(value = "0.01", message = "Minimum bet must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Limits can have at most 2 decimal places.")
            BigDecimal minBet,

            @NotNull(message = "A maximum bet is required.")
            @DecimalMin(value = "0.01", message = "Maximum bet must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Limits can have at most 2 decimal places.")
            BigDecimal maxBet) {
    }

    /** One game's accepted wager range. */
    public record GameLimitsView(BigDecimal minBet, BigDecimal maxBet) {
    }

    /**
     * The limits now in force for every game.
     *
     * @param games keyed by game name, so the console can render a row per game without
     *              hard-coding which games exist
     */
    public record LimitsResponse(java.util.Map<String, GameLimitsView> games,
                                 BigDecimal maxConfigurableBet) {

        public static LimitsResponse from(AdminService.LimitsResult result) {
            var games = new java.util.LinkedHashMap<String, GameLimitsView>();
            result.games().forEach((game, limits) ->
                    games.put(game, new GameLimitsView(limits.min(), limits.max())));
            return new LimitsResponse(games, result.maxConfigurableBet());
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
