package com.casino.web.dto;

import com.casino.domain.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read models for the account endpoints. */
public final class AccountResponses {

    private AccountResponses() {
    }

    /**
     * The signed-in caller.
     *
     * @param uid for a guest this is the session id, which is also what an admin credits against
     */
    public record MeResponse(String uid, String username, String role, BigDecimal balance, boolean guest) {
    }

    /** Confirmation that an account was closed. */
    public record DeletedResponse(String kind, String username, String message) {
    }

    /** One line of account history. Guests have none, by design. */
    public record LedgerEntryView(
            String type,
            String game,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String roundId,
            String detail,
            Instant at) {

        public static LedgerEntryView from(LedgerEntry entry) {
            return new LedgerEntryView(
                    entry.getEntryType().name(),
                    entry.getGame().name(),
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    entry.getRoundId(),
                    entry.getDetail(),
                    entry.getCreatedAt());
        }
    }

    /**
     * Lifetime play totals, over the whole ledger rather than the page being shown.
     *
     * @param wagered  everything staked, as a positive figure
     * @param returned everything paid back, stakes on wins and pushes included
     * @param net      returned minus wagered: positive means the account is up on the house
     */
    public record PlayTotals(BigDecimal wagered, BigDecimal returned, BigDecimal net) {

        public static PlayTotals of(BigDecimal wagered, BigDecimal returned) {
            return new PlayTotals(wagered, returned, returned.subtract(wagered));
        }

        public static PlayTotals none() {
            return new PlayTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public record HistoryResponse(List<LedgerEntryView> entries, PlayTotals totals) {
    }
}
