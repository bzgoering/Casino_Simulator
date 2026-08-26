package com.casino.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A persisted player or admin account.
 *
 * <p>Only registered users are stored. Guests are deliberately absent from this table.
 *
 * <p>The {@code version} column gives optimistic locking on the balance. Two concurrent bets on
 * the same account would otherwise be able to interleave their read-modify-write and lose one of
 * the debits, which is the classic way a gambling backend leaks money.
 */
@Entity
@Table(name = "user_account", indexes = {
        @Index(name = "idx_user_account_username", columnList = "username", unique = true),
        @Index(name = "idx_user_account_uid", columnList = "uid", unique = true)
})
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The public identifier handed to the user at sign-up and used by admins to credit an
     * account. A random UUID rather than the primary key, so the database row count and
     * registration order are not leaked.
     */
    @Column(name = "uid", nullable = false, unique = true, updatable = false, length = 36)
    private String uid;

    @Column(name = "username", nullable = false, unique = true, length = 32)
    private String username;

    /** BCrypt hash. The plaintext password is never stored or logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** Set when repeated failed logins lock the account; null when not locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected UserAccount() {
        // for JPA
    }

    public UserAccount(String username, String passwordHash, Role role, BigDecimal balance) {
        this.uid = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.balance = balance;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void recordFailedLogin(int maxAttempts, java.time.Duration lockDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            this.failedLoginAttempts = 0;
        }
        touch();
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        touch();
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        touch();
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
        touch();
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
