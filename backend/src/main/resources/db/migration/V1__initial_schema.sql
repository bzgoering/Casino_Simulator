-- Casino schema, initial version.
--
-- Only registered accounts (PLAYER, ADMIN) exist here. Guests are intentionally not persisted:
-- their balance lives in a short-lived server-side session and is discarded on expiry.
--
-- All money is NUMERIC(19,2). Never floating point: a binary float cannot represent 0.10
-- exactly, and a ledger that drifts a cent per round is a real defect.

CREATE TABLE user_account (
    id                     BIGSERIAL      PRIMARY KEY,
    uid                    VARCHAR(36)    NOT NULL,
    username               VARCHAR(32)    NOT NULL,
    password_hash          VARCHAR(100)   NOT NULL,
    role                   VARCHAR(16)    NOT NULL,
    balance                NUMERIC(19, 2) NOT NULL,
    enabled                BOOLEAN        NOT NULL DEFAULT TRUE,
    failed_login_attempts  INTEGER        NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT chk_user_role    CHECK (role IN ('PLAYER', 'ADMIN')),
    -- The house never extends credit; a balance can never go negative.
    CONSTRAINT chk_user_balance CHECK (balance >= 0)
);

CREATE UNIQUE INDEX idx_user_account_username ON user_account (LOWER(username));
CREATE UNIQUE INDEX idx_user_account_uid      ON user_account (uid);

-- Append-only. The sum of a user's entries must always equal their current balance.
CREATE TABLE ledger_entry (
    id             BIGSERIAL      PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
    entry_type     VARCHAR(24)    NOT NULL,
    game           VARCHAR(16)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    balance_after  NUMERIC(19, 2) NOT NULL,
    round_id       VARCHAR(36),
    detail         VARCHAR(512),
    created_at     TIMESTAMPTZ    NOT NULL,

    CONSTRAINT chk_ledger_type CHECK (entry_type IN ('SIGNUP_GRANT', 'BET', 'PAYOUT', 'ADMIN_CREDIT')),
    CONSTRAINT chk_ledger_game CHECK (game IN ('BLACKJACK', 'SLOTS', 'ROULETTE', 'ACCOUNT'))
);

CREATE INDEX idx_ledger_user_created ON ledger_entry (user_id, created_at DESC);
CREATE INDEX idx_ledger_round        ON ledger_entry (round_id);

-- Privileged actions are audited separately from the ledger: a guest credit moves money that has
-- no ledger, and the audit trail must outlive the target account.
CREATE TABLE admin_audit (
    id              BIGSERIAL      PRIMARY KEY,
    actor_uid       VARCHAR(36)    NOT NULL,
    actor_username  VARCHAR(32)    NOT NULL,
    action          VARCHAR(32)    NOT NULL,
    target_ref      VARCHAR(64)    NOT NULL,
    target_kind     VARCHAR(16)    NOT NULL,
    amount          NUMERIC(19, 2),
    source_ip       VARCHAR(45),
    created_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_admin_audit_actor ON admin_audit (actor_uid, created_at DESC);
