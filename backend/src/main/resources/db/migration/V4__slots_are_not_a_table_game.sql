-- Slots leave the admin-managed table limits.
--
-- A slot machine is not a table game. It has no house minimum: the player dials in a
-- denomination, down to a cent, and buys a fixed number of credits. Its only ceiling is the
-- per-spin guard in application.yml, which is deliberately not reachable from the admin console.
--
-- The row is dropped and the constraint tightened, so a stale SLOTS row cannot be written again
-- and then silently ignored by a backend that no longer reads it.

DELETE FROM game_limits WHERE game = 'SLOTS';

ALTER TABLE game_limits DROP CONSTRAINT chk_game_limits_game;
ALTER TABLE game_limits ADD CONSTRAINT chk_game_limits_game
    CHECK (game IN ('BLACKJACK', 'ROULETTE'));
