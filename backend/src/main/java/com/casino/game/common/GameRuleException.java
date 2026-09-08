package com.casino.game.common;

/**
 * A move or a bet the rules do not allow.
 *
 * <p>Thrown by the game engines when the request itself is at fault: a chip that no cloth could
 * hold, a split on two cards that do not pair, a pocket that is not on the wheel. The message
 * describes the rule that was broken and is written to be shown to the player, which is what
 * separates this from an ordinary {@link IllegalArgumentException}.
 *
 * <p>That distinction is the point. The error handler passes this message through to the client
 * and replaces the message on every other {@code IllegalArgumentException} with a generic one,
 * because those come from library and framework code and can quote internal detail.
 *
 * <p>It extends {@link IllegalArgumentException} so that callers and tests which reason about
 * "the engine rejected this" keep working unchanged.
 */
public class GameRuleException extends IllegalArgumentException {

    public GameRuleException(String message) {
        super(message);
    }
}
