package com.casino.game.blackjack;

import com.casino.game.common.Card;
import com.casino.game.common.Money;
import com.casino.game.common.Rank;
import com.casino.game.common.CardSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single round of blackjack, from the opening deal through settlement.
 *
 * <p>The round owns the authoritative state. Every card comes off the shared {@link CardSource}, so the
 * browser can never influence what is dealt; it only submits actions, which are validated here
 * against {@link BlackjackHand#legalActions}.
 *
 * <p>Sequence: deal two cards to the player and two to the dealer (one face down), peek for a
 * dealer natural when the upcard is an ace or a ten, let the player act on each hand in turn,
 * then draw for the dealer and score.
 *
 * <p>Not thread-safe: the service layer serialises access per session.
 */
public final class BlackjackRound {

    private final BlackjackRules rules;
    private final CardSource shoe;
    private final List<BlackjackHand> hands = new ArrayList<>();
    private final List<Card> dealerCards = new ArrayList<>();

    private final int startingHands;

    private RoundPhase phase = RoundPhase.PLAYER_TURN;
    private int activeHandIndex;
    private boolean dealerHoleRevealed;
    private BigDecimal totalStaked;

    public BlackjackRound(BlackjackRules rules, CardSource shoe, BigDecimal bet) {
        this(rules, shoe, bet, 1);
    }

    /**
     * Deals a round across {@code handCount} boxes, each carrying the same opening bet.
     *
     * <p>Multiple boxes are dealt exactly as a live table does: one card to every box in order,
     * then the dealer upcard, then a second card to every box, then the hole card. Dealing each
     * box to completion in turn would consume the shoe in a different order and quietly change
     * the game.
     */
    public BlackjackRound(BlackjackRules rules, CardSource shoe, BigDecimal bet, int handCount) {
        if (handCount < 1) {
            throw new IllegalArgumentException("handCount must be >= 1");
        }
        this.rules = rules;
        this.shoe = shoe;
        this.startingHands = handCount;
        this.totalStaked = Money.scaled(bet).multiply(BigDecimal.valueOf(handCount));

        for (int i = 0; i < handCount; i++) {
            hands.add(new BlackjackHand(bet, false, false, 0));
        }

        // Real dealing order: a card to every box, dealer up, a second card to every box,
        // dealer hole.
        for (BlackjackHand hand : hands) {
            hand.addCard(shoe.deal());
        }
        dealerCards.add(shoe.deal());
        for (BlackjackHand hand : hands) {
            hand.addCard(shoe.deal());
        }
        dealerCards.add(shoe.deal());

        resolveOpeningNaturals();
    }

    /**
     * Dealer peek plus any player naturals. If the dealer shows an ace or a ten they check the
     * hole card immediately, which ends the round before the player can risk any more money.
     *
     * <p>With several boxes in play a natural finishes only the box holding it: the remaining
     * boxes still have to be played out, so the round ends here only when the dealer turns a
     * natural or every box is already resolved.
     */
    private void resolveOpeningNaturals() {
        boolean dealerCouldHaveNatural = dealerUpcard().rank() == Rank.ACE
                || dealerUpcard().rank().blackjackValue() == 10;
        boolean dealerHasNatural = dealerCouldHaveNatural && HandValue.evaluate(dealerCards).is21();

        for (BlackjackHand hand : hands) {
            if (hand.isNaturalBlackjack()) {
                hand.setStatus(HandStatus.BLACKJACK);
            }
        }
        if (dealerHasNatural) {
            settle();
            return;
        }
        advance();
    }

    /** Applies a player decision to the currently active hand. */
    public void apply(PlayerAction action, BigDecimal availableBalance) {
        if (phase == RoundPhase.SETTLED) {
            throw new IllegalStateException("Round is already settled");
        }
        BlackjackHand hand = activeHand();
        List<PlayerAction> legal = hand.legalActions(rules, availableBalance, hands.size(), maxHands());
        if (!legal.contains(action)) {
            throw new IllegalArgumentException("Illegal action " + action + "; legal actions are " + legal);
        }

        switch (action) {
            case HIT -> {
                hand.addCard(shoe.deal());
                if (hand.value().isBust()) {
                    hand.setStatus(HandStatus.BUST);
                } else if (hand.value().is21()) {
                    // 21 never benefits from another card.
                    hand.setStatus(HandStatus.STAND);
                }
            }
            case STAND -> hand.setStatus(HandStatus.STAND);
            case DOUBLE -> {
                totalStaked = Money.scaled(totalStaked.add(hand.bet()));
                hand.setBet(hand.bet().multiply(BigDecimal.valueOf(2)));
                hand.markDoubled();
                hand.addCard(shoe.deal());
                hand.setStatus(hand.value().isBust() ? HandStatus.BUST : HandStatus.STAND);
            }
            case SPLIT -> split(hand);
        }

        advance();
    }

    private void split(BlackjackHand hand) {
        boolean aces = hand.cards().get(0).rank() == Rank.ACE;
        Card moved = hand.removeLastCard();

        BlackjackHand created =
                new BlackjackHand(hand.bet(), true, aces, hand.splitDepth() + 1);
        created.addCard(moved);
        // Both halves of a split are split hands, the original object included.
        hand.markAsSplitHand(aces);
        totalStaked = Money.scaled(totalStaked.add(hand.bet()));

        // The new hand is played immediately after the one it came from.
        hands.add(activeHandIndex + 1, created);

        hand.addCard(shoe.deal());
        created.addCard(shoe.deal());

        // Split aces receive one card each and are then finished.
        if (aces) {
            hand.setStatus(HandStatus.STAND);
            created.setStatus(HandStatus.STAND);
        }
    }

    /** Moves to the next hand needing a decision, or runs the dealer and settles. */
    private void advance() {
        while (activeHandIndex < hands.size() && hands.get(activeHandIndex).isResolved()) {
            activeHandIndex++;
        }
        if (activeHandIndex >= hands.size()) {
            playDealer();
            settle();
        }
    }

    /**
     * Dealer draws to 17. Skipped entirely when every player hand has busted, since the dealer
     * total cannot change the result, matching how a live dealer just takes the bets.
     */
    private void playDealer() {
        dealerHoleRevealed = true;
        boolean anyLiveHand = hands.stream()
                .anyMatch(h -> h.status() != HandStatus.BUST && h.status() != HandStatus.BLACKJACK);
        if (!anyLiveHand) {
            return;
        }
        while (true) {
            HandValue value = HandValue.evaluate(dealerCards);
            boolean mustHit = value.total() < 17
                    || (value.total() == 17 && value.soft() && rules.dealerHitsSoft17());
            if (!mustHit) {
                return;
            }
            dealerCards.add(shoe.deal());
        }
    }

    private void settle() {
        dealerHoleRevealed = true;
        HandValue dealer = HandValue.evaluate(dealerCards);
        boolean dealerNatural = dealerCards.size() == 2 && dealer.is21();

        for (BlackjackHand hand : hands) {
            HandValue player = hand.value();

            if (hand.status() == HandStatus.BLACKJACK) {
                if (dealerNatural) {
                    settleHand(hand, HandOutcome.PUSH, hand.bet());
                } else {
                    BigDecimal winnings = Money.multiply(hand.bet(), rules.blackjackPayout());
                    settleHand(hand, HandOutcome.BLACKJACK, hand.bet().add(winnings));
                }
            } else if (player.isBust()) {
                settleHand(hand, HandOutcome.LOSE, Money.ZERO);
            } else if (dealerNatural) {
                settleHand(hand, HandOutcome.LOSE, Money.ZERO);
            } else if (dealer.isBust() || player.total() > dealer.total()) {
                settleHand(hand, HandOutcome.WIN, hand.bet().multiply(BigDecimal.valueOf(2)));
            } else if (player.total() < dealer.total()) {
                settleHand(hand, HandOutcome.LOSE, Money.ZERO);
            } else {
                settleHand(hand, HandOutcome.PUSH, hand.bet());
            }

            if (hand.status() == HandStatus.ACTIVE) {
                hand.setStatus(HandStatus.STAND);
            }
        }
        phase = RoundPhase.SETTLED;
    }

    private void settleHand(BlackjackHand hand, HandOutcome outcome, BigDecimal payout) {
        hand.setOutcome(outcome);
        hand.setPayout(payout);
    }

    public BlackjackHand activeHand() {
        if (activeHandIndex >= hands.size()) {
            throw new IllegalStateException("No active hand");
        }
        return hands.get(activeHandIndex);
    }

    public int activeHandIndex() {
        return activeHandIndex;
    }

    public List<BlackjackHand> hands() {
        return Collections.unmodifiableList(hands);
    }

    /** Boxes dealt at the start of the round, before any split. */
    public int startingHands() {
        return startingHands;
    }

    /**
     * Ceiling on hands in play. Each box may be split up to the table maximum, so the allowance
     * scales with the number of boxes rather than being a flat cap that a four-box round would
     * already exceed before a single split.
     */
    private int maxHands() {
        return startingHands * (rules.maxSplits() + 1);
    }

    public Card dealerUpcard() {
        return dealerCards.get(0);
    }

    /** Dealer cards as the player may see them; the hole card stays hidden until the reveal. */
    public List<Card> visibleDealerCards() {
        return dealerHoleRevealed
                ? Collections.unmodifiableList(dealerCards)
                : List.of(dealerCards.get(0));
    }

    public HandValue dealerValue() {
        return dealerHoleRevealed
                ? HandValue.evaluate(dealerCards)
                : HandValue.evaluate(List.of(dealerCards.get(0)));
    }

    public boolean isDealerHoleRevealed() {
        return dealerHoleRevealed;
    }

    public RoundPhase phase() {
        return phase;
    }

    public boolean isSettled() {
        return phase == RoundPhase.SETTLED;
    }

    /** Every chip committed this round: the opening bet plus any doubles and splits. */
    public BigDecimal totalStaked() {
        return totalStaked;
    }

    /** Everything returned to the player, stake included. */
    public BigDecimal totalPayout() {
        return Money.scaled(hands.stream()
                .map(BlackjackHand::payout)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Positive when the player finished the round up. */
    public BigDecimal netResult() {
        return Money.scaled(totalPayout().subtract(totalStaked));
    }

    public List<PlayerAction> legalActions(BigDecimal availableBalance) {
        if (phase == RoundPhase.SETTLED) {
            return List.of();
        }
        return activeHand().legalActions(rules, availableBalance, hands.size(), maxHands());
    }
}
