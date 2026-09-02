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
        /** The highest maximum bet an administrator may set, on any game. */
        BigDecimal maxConfigurableBet,
        int maxRouletteBets,
        /** Shortest acceptable password, so the sign-up and change forms need not restate it. */
        int passwordMinLength,
        BlackjackInfo blackjack,
        SlotsInfo slots,
        RouletteInfo roulette) {

    /**
     * @param maxHands most boxes one player may take in a single round
     * @param minBet   smallest accepted wager on this game
     * @param maxBet   largest accepted wager on this game
     */
    public record BlackjackInfo(
            int decks,
            boolean dealerHitsSoft17,
            String blackjackPays,
            int maxSplits,
            boolean doubleAfterSplit,
            int maxHands,
            BigDecimal minBet,
            BigDecimal maxBet) {
    }

    /**
     * @param paytable combination name to payout multiplier
     * @param rtp      the machine's exact return to player per line, as a percentage. The figure
     *                 does not move with the number of lines lit: each line is an independent
     *                 draw at the same price
     */
    /**
     * @param creditOptions the fixed credit buttons; each credit lights one more payline
     * @param paylines      every line the machine pays on, in the order credits light them
     * @param maxTotalBet   ceiling on bet-per-line times credits for one spin
     */
    public record SlotsInfo(List<String> reelStrip, Map<String, Integer> paytable, double rtp,
                           List<Integer> creditOptions, List<PaylineInfo> paylines,
                           BigDecimal maxTotalBet) {
    }

    /**
     * One payline.
     *
     * @param rows the row this line reads on each reel, top row 0
     */
    public record PaylineInfo(String id, String name, List<Integer> rows) {
    }

    /**
     * @param pocketOrder the physical wheel sequence, for drawing the wheel
     * @param payouts     bet type to odds-to-one
     */
    public record RouletteInfo(List<Integer> pocketOrder, Map<String, Integer> payouts,
                              double houseEdgePercent, BigDecimal minBet, BigDecimal maxBet) {
    }
}
