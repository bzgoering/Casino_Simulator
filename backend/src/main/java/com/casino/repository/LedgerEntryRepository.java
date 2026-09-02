package com.casino.repository;

import com.casino.domain.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /**
     * Removes an account's whole ledger.
     *
     * <p>The foreign key cascades on delete in PostgreSQL, but the entity maps {@code user_id} as
     * a plain column rather than a relation, so nothing in the application layer guarantees that
     * cascade exists on whatever schema it is pointed at. Deleting explicitly makes closing an
     * account mean the same thing everywhere instead of depending on a database feature.
     */
    long deleteByUserId(Long userId);

    /**
     * Money staked and money returned across every game the account has played.
     *
     * <p>Aggregated in the database over the whole ledger rather than over the page the history
     * screen happens to be showing, so the total does not change when the player asks for more
     * rows. Sign-up grants and admin credits are excluded: they are not a result of play, and
     * folding them in would show a new player up $100 before their first bet.
     *
     * @return one row: total wagered as a positive figure, and total returned
     */
    @Query("""
            select coalesce(-sum(case when e.entryType = com.casino.domain.LedgerEntryType.BET
                                      then e.amount else 0 end), 0),
                   coalesce(sum(case when e.entryType = com.casino.domain.LedgerEntryType.PAYOUT
                                     then e.amount else 0 end), 0)
            from LedgerEntry e
            where e.userId = :userId
              and e.game <> com.casino.domain.GameType.ACCOUNT
            """)
    List<Object[]> playTotalsRaw(@Param("userId") Long userId);

    /** Total wagered and total returned across all play, both non-negative. */
    default BigDecimal[] playTotals(Long userId) {
        List<Object[]> rows = playTotalsRaw(userId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO };
        }
        Object[] row = rows.get(0);
        return new BigDecimal[] {
                row[0] == null ? BigDecimal.ZERO : (BigDecimal) row[0],
                row[1] == null ? BigDecimal.ZERO : (BigDecimal) row[1],
        };
    }
}
