-- Betting limits become per game.
--
-- One row per game rather than one row for the house: blackjack, slots and roulette are
-- different products with different economics, and a single pair of bounds forced a $1 slot
-- minimum on a blackjack table or a $5,000 roulette chip on the slots.
--
-- Whatever limits an administrator had already set are carried across to every game, so the
-- change is invisible to a running deployment. As before, a game with no row falls back to the
-- values in application.yml, which keeps a fresh database working with no seed step.

CREATE TABLE game_limits (
    game        VARCHAR(16)    PRIMARY KEY,
    min_bet     NUMERIC(19, 2) NOT NULL,
    max_bet     NUMERIC(19, 2) NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    updated_by  VARCHAR(32),

    CONSTRAINT chk_game_limits_game     CHECK (game IN ('BLACKJACK', 'SLOTS', 'ROULETTE')),
    CONSTRAINT chk_game_limits_positive CHECK (min_bet > 0),
    CONSTRAINT chk_game_limits_ordered  CHECK (max_bet >= min_bet)
);

INSERT INTO game_limits (game, min_bet, max_bet, updated_at, updated_by)
SELECT g.game, t.min_bet, t.max_bet, t.updated_at, t.updated_by
FROM table_limits t
CROSS JOIN (VALUES ('BLACKJACK'), ('SLOTS'), ('ROULETTE')) AS g(game)
WHERE t.id = 1;

DROP TABLE table_limits;
