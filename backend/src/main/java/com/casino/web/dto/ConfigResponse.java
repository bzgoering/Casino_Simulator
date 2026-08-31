package com.casino.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Public table information, served unauthenticated so the lobby can render limits and paytables
 * before anyone signs in.
 *
 * <p>This is presentation only. Every value here is also enforced server-side; nothing the client
 * reads from this endpoint is trusted when a bet comes back.
 */
public record ConfigResponse(
        BigDecimal minBet,
        BigDecimal maxBet,
        /** The highest maximum bet an administrator may set. */
        BigDecimal maxConfigurableBet,
        int maxRouletteBets,
        BlackjackInfo blackjack,
        SlotsInfo slots,
        RouletteInfo roulette) {

    /**
     * @param maxHands most boxes one player may take in a single round
     */
    public record BlackjackInfo(
            int decks,
            boolean dealerHitsSoft17,
            String blackjackPays,
            int maxSplits,
            boolean doubleAfterSplit,
            int maxHands) {
    }

    /**
     * @param paytable combination name to payout multiplier
     * @param rtp      the machine's exact return to player, as a percentage
     */
    public record SlotsInfo(List<String> reelStrip, Map<String, Integer> paytable, double rtp) {
    }

    /**
     * @param pocketOrder the physical wheel sequence, for drawing the wheel
     * @param payouts     bet type to odds-to-one
     */
    public record RouletteInfo(List<Integer> pocketOrder, Map<String, Integer> payouts, double houseEdgePercent) {
    }
}
