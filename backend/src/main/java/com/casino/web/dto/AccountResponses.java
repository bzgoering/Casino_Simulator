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

    public record HistoryResponse(List<LedgerEntryView> entries) {
    }
}
