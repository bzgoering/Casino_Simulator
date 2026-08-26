package com.casino.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Append-only audit of privileged actions.
 *
 * <p>The ability to mint balance is the most abusable privilege in the system, so every use of it
 * is recorded separately from the ledger: who did it, to which account, how much, from what
 * address. Kept distinct from {@link LedgerEntry} because a guest credit moves money that has no
 * ledger at all, and because the audit must survive even if the target account is later removed.
 */
@Entity
@Table(name = "admin_audit", indexes = {
        @Index(name = "idx_admin_audit_actor", columnList = "actor_uid,created_at")
})
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_uid", nullable = false, updatable = false, length = 36)
    private String actorUid;

    @Column(name = "actor_username", nullable = false, updatable = false, length = 32)
    private String actorUsername;

    @Column(name = "action", nullable = false, updatable = false, length = 32)
    private String action;

    /** UID of a registered account, or the session id of a guest. */
    @Column(name = "target_ref", nullable = false, updatable = false, length = 64)
    private String targetRef;

    @Column(name = "target_kind", nullable = false, updatable = false, length = 16)
    private String targetKind;

    @Column(name = "amount", precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "source_ip", updatable = false, length = 45)
    private String sourceIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntry() {
        // for JPA
    }

    public AdminAuditEntry(String actorUid, String actorUsername, String action, String targetRef,
                           String targetKind, BigDecimal amount, String sourceIp) {
        this.actorUid = actorUid;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetRef = targetRef;
        this.targetKind = targetKind;
        this.amount = amount;
        this.sourceIp = sourceIp;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getActorUid() {
        return actorUid;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public String getTargetKind() {
        return targetKind;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
