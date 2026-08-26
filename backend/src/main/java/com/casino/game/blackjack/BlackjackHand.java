package com.casino.game.blackjack;

import com.casino.game.common.Card;
import com.casino.game.common.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One player hand within a round. A split produces additional hands. */
public final class BlackjackHand {

    private final List<Card> cards = new ArrayList<>();
    private BigDecimal bet;
    private HandStatus status = HandStatus.ACTIVE;
    private HandOutcome outcome;
    private BigDecimal payout = Money.ZERO;
    private boolean doubled;
    private boolean fromSplit;
    private boolean splitAce;
    private int splitDepth;

    BlackjackHand(BigDecimal bet, boolean fromSplit, boolean splitAce, int splitDepth) {
        this.bet = Money.scaled(bet);
        this.fromSplit = fromSplit;
        this.splitAce = splitAce;
        this.splitDepth = splitDepth;
    }

    void addCard(Card card) {
        cards.add(card);
    }

    Card removeLastCard() {
        return cards.remove(cards.size() - 1);
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public HandValue value() {
        return HandValue.evaluate(cards);
    }

    public BigDecimal bet() {
        return bet;
    }

    void setBet(BigDecimal bet) {
        this.bet = Money.scaled(bet);
    }

    public HandStatus status() {
        return status;
    }

    void setStatus(HandStatus status) {
        this.status = status;
    }

    public HandOutcome outcome() {
        return outcome;
    }

    void setOutcome(HandOutcome outcome) {
        this.outcome = outcome;
    }

    /** Total returned to the player for this hand, stake included. Zero on a loss. */
    public BigDecimal payout() {
        return payout;
    }

    void setPayout(BigDecimal payout) {
        this.payout = Money.scaled(payout);
    }

    public boolean isDoubled() {
        return doubled;
    }

    void markDoubled() {
        this.doubled = true;
    }

    /**
     * Marks this hand as one of the two produced by a split.
     *
     * <p>Applied to <em>both</em> resulting hands, including the one that keeps the original
     * object. Leaving the original unmarked would let a two-card 21 on it read as a natural and
     * would exempt it from the table's double-after-split rule.
     */
    void markAsSplitHand(boolean ace) {
        this.fromSplit = true;
        this.splitAce = ace;
        this.splitDepth++;
    }

    public boolean isFromSplit() {
        return fromSplit;
    }

    public boolean isSplitAce() {
        return splitAce;
    }

    int splitDepth() {
        return splitDepth;
    }

    /** A natural counts only on the initial two cards; 21 after a split is an ordinary 21. */
    public boolean isNaturalBlackjack() {
        return !fromSplit && cards.size() == 2 && value().is21();
    }

    public boolean isResolved() {
        return status != HandStatus.ACTIVE;
    }

    /**
     * Legal actions right now. Split aces get exactly one card and cannot act again;
     * a doubled hand is likewise finished.
     */
    public List<PlayerAction> legalActions(BlackjackRules rules, BigDecimal availableBalance, int handCount) {
        List<PlayerAction> actions = new ArrayList<>();
        if (isResolved()) {
            return actions;
        }
        actions.add(PlayerAction.HIT);
        actions.add(PlayerAction.STAND);

        boolean firstDecision = cards.size() == 2;
        boolean canAffordAnotherBet = availableBalance.compareTo(bet) >= 0;

        if (firstDecision && canAffordAnotherBet
                && (!fromSplit || rules.doubleAfterSplit())) {
            actions.add(PlayerAction.DOUBLE);
        }
        if (firstDecision && canAffordAnotherBet
                && splitDepth < rules.maxSplits()
                && handCount < rules.maxSplits() + 1
                && cards.get(0).rank().blackjackValue() == cards.get(1).rank().blackjackValue()) {
            actions.add(PlayerAction.SPLIT);
        }
        return actions;
    }
}
