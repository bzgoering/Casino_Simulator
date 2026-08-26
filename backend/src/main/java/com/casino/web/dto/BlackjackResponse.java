package com.casino.web.dto;

import com.casino.game.blackjack.BlackjackHand;
import com.casino.game.blackjack.BlackjackRound;
import com.casino.game.blackjack.PlayerAction;
import com.casino.service.BlackjackService;
import java.math.BigDecimal;
import java.util.List;

/**
 * The table as the player is allowed to see it.
 *
 * <p>Mapping goes through {@link BlackjackRound#visibleDealerCards()} rather than the dealer's
 * full hand, so the hole card is genuinely absent from the payload until the reveal. Hiding it in
 * the UI while shipping it in the JSON would make the game trivially beatable with dev tools open.
 */
public record BlackjackResponse(
        String roundId,
        String phase,
        List<HandView> hands,
        int activeHandIndex,
        DealerView dealer,
        List<PlayerAction> legalActions,
        BigDecimal totalStaked,
        BigDecimal totalPayout,
        BigDecimal net,
        BigDecimal balance,
        int cardsRemaining,
        boolean settled) {

    /**
     * @param outcome null while the hand is still live
     * @param payout  total returned for this hand, stake included
     */
    public record HandView(
            List<String> cards,
            int total,
            boolean soft,
            String status,
            String outcome,
            BigDecimal bet,
            BigDecimal payout,
            boolean doubled,
            boolean fromSplit) {
    }

    /**
     * @param cards only the upcard until the dealer's hand is revealed
     * @param total the visible total, matching the visible cards
     */
    public record DealerView(List<String> cards, int total, boolean soft, boolean revealed) {
    }

    public static BlackjackResponse from(BlackjackService.BlackjackRoundView view) {
        BlackjackRound round = view.round();

        List<HandView> hands = round.hands().stream()
                .map(BlackjackResponse::toHandView)
                .toList();

        var dealerValue = round.dealerValue();
        DealerView dealer = new DealerView(
                round.visibleDealerCards().stream().map(c -> c.code()).toList(),
                dealerValue.total(),
                dealerValue.soft(),
                round.isDealerHoleRevealed());

        return new BlackjackResponse(
                view.roundId(),
                round.phase().name(),
                hands,
                round.activeHandIndex(),
                dealer,
                view.legalActions(),
                round.totalStaked(),
                round.isSettled() ? round.totalPayout() : null,
                round.isSettled() ? round.netResult() : null,
                view.balance(),
                view.cardsRemaining(),
                round.isSettled());
    }

    private static HandView toHandView(BlackjackHand hand) {
        var value = hand.value();
        return new HandView(
                hand.cards().stream().map(c -> c.code()).toList(),
                value.total(),
                value.soft(),
                hand.status().name(),
                hand.outcome() == null ? null : hand.outcome().name(),
                hand.bet(),
                hand.payout(),
                hand.isDoubled(),
                hand.isFromSplit());
    }
}
