package com.casino.game.common;

/**
 * A legal action asked for at the wrong moment.
 *
 * <p>Acting on a round that has already settled, for instance. The request is not malformed and
 * the player is not cheating; the game has simply moved on, so the answer is a conflict rather
 * than a bad request, and the message is safe to show.
 *
 * <p>As with {@link GameRuleException}, the handler passes this message through while an
 * unexpected {@link IllegalStateException} is treated as a server fault and answered generically,
 * so that a genuine internal error is not quietly reported to the client as a routine conflict.
 */
public class GameStateException extends IllegalStateException {

    public GameStateException(String message) {
        super(message);
    }
}
