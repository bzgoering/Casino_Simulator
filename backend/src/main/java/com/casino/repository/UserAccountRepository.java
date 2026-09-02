package com.casino.repository;

import com.casino.domain.Role;
import com.casino.domain.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByUid(String uid);

    boolean existsByUsernameIgnoreCase(String username);

    /** Used to refuse the deletion that would leave nobody able to administer the casino. */
    long countByRole(Role role);

    /**
     * Loads an account with a row-level write lock for the duration of the transaction.
     *
     * <p>Used on the wager path so that two simultaneous bets on the same account serialise at
     * the database rather than racing each other. Without this, a player could fire concurrent
     * requests and spend the same balance twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
