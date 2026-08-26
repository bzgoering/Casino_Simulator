package com.casino.game.blackjack;

import com.casino.game.common.Card;
import com.casino.game.common.Rank;
import java.util.List;

/**
 * The best total for a set of cards.
 *
 * @param total the highest total that does not bust, or the busted total if all options bust
 * @param soft  true when an ace is still counted as 11 (the hand cannot bust on the next card)
 */
public record HandValue(int total, boolean soft) {

    public boolean isBust() {
        return total > 21;
    }

    public boolean is21() {
        return total == 21;
    }

    /**
     * Counts every ace as 11, then demotes aces to 1 one at a time while the hand is over 21.
     * This always yields the best legal total.
     */
    public static HandValue evaluate(List<Card> cards) {
        int total = 0;
        int aces = 0;
        for (Card card : cards) {
            total += card.rank().blackjackValue();
            if (card.rank() == Rank.ACE) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10; // demote one ace from 11 to 1
            aces--;
        }
        return new HandValue(total, aces > 0);
    }
}
