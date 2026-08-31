package com.casino.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Request bodies for the game endpoints. */
public final class GameRequests {

    private GameRequests() {
    }

    /** A wager on slots or an opening blackjack bet. */
    public record BetRequest(
            @NotNull(message = "A bet amount is required.")
            @DecimalMin(value = "0.01", message = "Bet must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Bet can have at most 2 decimal places.")
            BigDecimal bet) {
    }

    /**
     * An opening blackjack bet.
     *
     * @param bet   the stake on each box
     * @param hands how many boxes to play; absent means one. The server caps this and charges
     *              {@code bet} for every box, so four hands at $25 costs $100.
     */
    public record BlackjackDealRequest(
            @NotNull(message = "A bet amount is required.")
            @DecimalMin(value = "0.01", message = "Bet must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Bet can have at most 2 decimal places.")
            BigDecimal bet,

            @Min(value = 1, message = "Play at least one hand.")
            @Max(value = 8, message = "Too many hands.")
            Integer hands) {

        /** Boxes requested, defaulting to one when the client does not say. */
        public int handCount() {
            return hands == null ? 1 : hands;
        }
    }

    /**
     * A blackjack decision.
     *
     * @param roundId the round this action belongs to, so a stale retry cannot act on the next hand
     * @param action  HIT, STAND, DOUBLE or SPLIT
     */
    public record BlackjackActionRequest(
            @Size(max = 36, message = "Invalid round id.")
            String roundId,

            @NotNull(message = "An action is required.")
            com.casino.game.blackjack.PlayerAction action) {
    }

    /** One chip on the roulette layout. */
    public record RouletteBetRequest(
            @NotNull(message = "A bet type is required.")
            com.casino.game.roulette.RouletteBetType type,

            @NotNull(message = "A selection is required.")
            @Size(max = 32, message = "Selection is too long.")
            String selection,

            @NotNull(message = "A bet amount is required.")
            @DecimalMin(value = "0.01", message = "Bet must be greater than zero.")
            @Digits(integer = 12, fraction = 2, message = "Bet can have at most 2 decimal places.")
            BigDecimal amount) {
    }

    /** Every chip placed before a single spin. */
    public record RouletteSpinRequest(
            @NotEmpty(message = "Place at least one bet before spinning.")
            @Size(max = 32, message = "Too many bets on one spin.")
            @Valid
            List<RouletteBetRequest> bets) {
    }
}
