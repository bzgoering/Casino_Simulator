-- House-adjustable table limits.
--
-- A single row, so the constraint that there is exactly one set of limits is enforced by the
-- database rather than by convention. The row is created on first write: while it is absent the
-- backend falls back to the values in application.yml, which keeps a fresh database working
-- without a seed step.

CREATE TABLE table_limits (
    id          SMALLINT       PRIMARY KEY,
    min_bet     NUMERIC(19, 2) NOT NULL,
    max_bet     NUMERIC(19, 2) NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    updated_by  VARCHAR(32),

    CONSTRAINT chk_table_limits_singleton CHECK (id = 1),
    CONSTRAINT chk_table_limits_positive  CHECK (min_bet > 0),
    CONSTRAINT chk_table_limits_ordered   CHECK (max_bet >= min_bet)
);
