package com.casino.service;

import com.casino.config.CasinoProperties;
import com.casino.domain.GameType;
import com.casino.domain.LedgerEntryType;
import com.casino.game.blackjack.BlackjackRound;
import com.casino.game.blackjack.BlackjackRules;
import com.casino.game.blackjack.PlayerAction;
import com.casino.game.common.Money;
import com.casino.game.common.RandomSource;
import com.casino.game.common.Shoe;
import com.casino.security.CasinoPrincipal;
import com.casino.web.error.CasinoException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives blackjack, which unlike the other games spans several requests.
 *
 * <p>All round state is held here, server side. The browser sends only the action it wants; it is
 * never trusted with the shoe, the hole card or the hand totals, which is what makes the game
 * unwinnable by tampering with the client.
 *
 * <p>Extra money for a double or a split is taken at the moment the action is applied, so the
 * stake on the table always matches what has actually been debited.
 */
@Service
public class BlackjackService {

    private static final Duration TABLE_TTL = Duration.ofHours(2);

    private final Map<String, BlackjackTable> tables = new ConcurrentHashMap<>();
    private final RandomSource random;
    private final WalletService wallet;
    private final BetValidator betValidator;
    private final int maxHands;
    private final BlackjackRules rules = BlackjackRules.standard();

    public BlackjackService(RandomSource random, WalletService wallet, BetValidator betValidator,
                            CasinoProperties properties) {
        this.random = random;
        this.wallet = wallet;
        this.betValidator = betValidator;
        this.maxHands = Math.max(1, properties.limits().maxBlackjackHands());
    }

    /** Starts a new round on one box. */
    @Transactional
    public BlackjackRoundView deal(CasinoPrincipal principal, BigDecimal requestedBet) {
        return deal(principal, requestedBet, 1);
    }

    /**
     * Starts a new round across {@code handCount} boxes. Any previous settled round on this seat
     * is replaced.
     *
     * <p>Each box carries the same stake, and the whole thing is taken as one debit before a
     * card is dealt. Taking it per box would leave a player who can cover three of four boxes
     * holding a partly funded round.
     */
    @Transactional
    public BlackjackRoundView deal(CasinoPrincipal principal, BigDecimal requestedBet, int handCount) {
        int boxes = validateHandCount(handCount);
        BigDecimal stake = betValidator.validate(GameType.BLACKJACK, requestedBet);
        BigDecimal committed = Money.scaled(stake.multiply(BigDecimal.valueOf(boxes)));
        BlackjackTable table = tableFor(principal);

        return withTable(table, () -> {
            if (table.round() != null && !table.round().isSettled()) {
                throw CasinoException.conflict("Hand still in progress.");
            }
            // The cut card is honoured between rounds, never mid-hand.
            table.shoe().shuffleIfNeeded();

            String roundId = UUID.randomUUID().toString();
            wallet.debit(principal, committed, GameType.BLACKJACK, roundId,
                    boxes == 1 ? "Blackjack bet" : "Blackjack bet on " + boxes + " hands");

            BlackjackRound round = new BlackjackRound(rules, table.shoe(), stake, boxes);
            table.setRound(round);
            table.setRoundId(roundId);

            BigDecimal balance = settleIfFinished(principal, round, roundId);
            return view(table, balance);
        });
    }

    private int validateHandCount(int handCount) {
        if (handCount < 1) {
            throw CasinoException.badRequest("Play at least one hand.");
        }
        if (handCount > maxHands) {
            throw CasinoException.badRequest("At most " + maxHands + " hands.");
        }
        return handCount;
    }

    /** Applies HIT, STAND, DOUBLE or SPLIT to the active hand. */
    @Transactional
    public BlackjackRoundView act(CasinoPrincipal principal, String roundId, PlayerAction action) {
        BlackjackTable table = tableFor(principal);

        return withTable(table, () -> {
            BlackjackRound round = table.round();
            if (round == null) {
                throw CasinoException.notFound("No hand in progress.");
            }
            if (round.isSettled()) {
                throw CasinoException.conflict("Hand already finished.");
            }
            // Binding the action to a round id stops a stale retry from acting on the next hand.
            if (roundId != null && !roundId.equals(table.roundId())) {
                throw CasinoException.conflict("That hand ended.");
            }

            String activeRoundId = table.roundId();
            BigDecimal balance = wallet.balanceOf(principal);
            BigDecimal stakedBefore = round.totalStaked();

            round.apply(action, balance);

            // DOUBLE and SPLIT commit more money; take exactly the difference the engine added.
            BigDecimal additionalStake = Money.scaled(round.totalStaked().subtract(stakedBefore));
            if (Money.isPositive(additionalStake)) {
                balance = wallet.debit(principal, additionalStake, GameType.BLACKJACK, activeRoundId,
                        action + " on blackjack hand");
            }

            settleIfFinished(principal, round, activeRoundId);
            return view(table, wallet.balanceOf(principal));
        });
    }

    /** The current round on this seat, if there is one. */
    public BlackjackRoundView current(CasinoPrincipal principal) {
        BlackjackTable table = tables.get(principal.subject());
        if (table == null || table.round() == null) {
            throw CasinoException.notFound("No hand in progress.");
        }
        table.touch();
        return view(table, wallet.balanceOf(principal));
    }

    /**
     * Pays out when the round has finished, and returns the balance afterwards. A still-live
     * round simply reports the current balance.
     */
    private BigDecimal settleIfFinished(CasinoPrincipal principal, BlackjackRound round, String roundId) {
        if (!round.isSettled()) {
            return wallet.balanceOf(principal);
        }
        String detail = round.hands().size() == 1
                ? String.valueOf(round.hands().get(0).outcome())
                : round.hands().size() + " hands";
        return wallet.credit(principal, round.totalPayout(), LedgerEntryType.PAYOUT,
                GameType.BLACKJACK, roundId, detail);
    }

    private BlackjackRoundView view(BlackjackTable table, BigDecimal balance) {
        BlackjackRound round = table.round();
        return new BlackjackRoundView(
                table.roundId(),
                round,
                balance,
                round.legalActions(balance),
                table.shoe().cardsRemaining());
    }

    private BlackjackTable tableFor(CasinoPrincipal principal) {
        return tables.compute(principal.subject(), (key, existing) -> {
            BlackjackTable table = existing != null
                    ? existing
                    : new BlackjackTable(new Shoe(rules.deckCount(), rules.penetration(), random));
            table.touch();
            return table;
        });
    }

    /**
     * Runs the action while holding this seat's lock. A caller that cannot get the lock quickly
     * is told to retry rather than being queued indefinitely.
     */
    private <T> T withTable(BlackjackTable table, Supplier<T> action) {
        ReentrantLock lock = table.lock();
        boolean acquired;
        try {
            acquired = lock.tryLock(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CasinoException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted. Please try again.");
        }
        if (!acquired) {
            throw CasinoException.conflict("Action still processing.");
        }
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forgets a seat entirely, for an account that is going away.
     *
     * <p>Any round still open on it is abandoned. The stake was already debited and the account
     * is about to cease to exist, so there is nothing left to settle.
     */
    public void closeTable(String subject) {
        tables.remove(subject);
    }

    /** Drops seats nobody has used for a while, so idle tables do not accumulate. */
    @Scheduled(fixedDelayString = "PT10M")
    public void evictIdleTables() {
        tables.values().removeIf(table -> table.isExpired(TABLE_TTL));
    }

    public BlackjackRules rules() {
        return rules;
    }

    /** Most boxes one player may take in a single round. */
    public int maxHands() {
        return maxHands;
    }

    public int openTableCount() {
        return tables.size();
    }

    /** A settled or in-progress round, ready to be mapped to the wire format. */
    public record BlackjackRoundView(
            String roundId,
            BlackjackRound round,
            BigDecimal balance,
            List<PlayerAction> legalActions,
            int cardsRemaining) {
    }
}
