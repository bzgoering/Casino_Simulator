package com.casino.web.error;

import com.casino.game.common.GameRuleException;
import com.casino.game.common.GameStateException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions into the one {@link ApiError} shape.
 *
 * <p>The guiding rule is that the client learns what it needs to correct its request and nothing
 * more. Expected failures carry their own message; anything unexpected is logged in full server
 * side and answered with a generic message, so a stack trace, SQL fragment or class name never
 * reaches an attacker.
 *
 * <p>Only the two game exceptions are trusted to carry prose to the client. A bare
 * {@link IllegalStateException} is deliberately <em>not</em> handled here: it falls through to
 * the catch-all and is reported as a 500, because an unexpected internal state is a server fault
 * and answering it with a tidy 409 would hide real bugs from anything watching the error rate.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CasinoException.class)
    public ResponseEntity<ApiError> handleCasino(CasinoException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus().value(), ex.getStatus().name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.validation(HttpStatus.BAD_REQUEST.value(),
                        "Some fields need attention.", fieldErrors));
    }

    /**
     * An illegal move or an unplaceable bet. These messages describe the rules ("Illegal action
     * SPLIT; legal actions are [HIT, STAND]") and are written for the player, so they are passed
     * through as-is.
     */
    @ExceptionHandler(GameRuleException.class)
    public ResponseEntity<ApiError> handleGameRule(GameRuleException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST", ex.getMessage()));
    }

    /** A legal action at the wrong moment: the round moved on. Also safe to relay. */
    @ExceptionHandler(GameStateException.class)
    public ResponseEntity<ApiError> handleGameState(GameStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "INVALID_STATE", ex.getMessage()));
    }

    /**
     * Any other {@link IllegalArgumentException}, which did not come from the game rules.
     *
     * <p>Still a 400 -- these are almost always a malformed argument off the request -- but the
     * message is <em>not</em> relayed. {@code IllegalArgumentException} is thrown throughout the
     * JDK, Spring, Hibernate and Jackson, and those messages can quote class names, entity names
     * and fragments of internal state. Only {@link GameRuleException} is trusted to be
     * player-facing prose.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        log.warn("Rejected request on {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.toString());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST",
                        "That request could not be accepted. Check the fields and try again."));
    }

    /**
     * A body Jackson cannot read: malformed JSON, a bad enum value, a string where a number was
     * expected. This is client error, not server error, and must not reach the catch-all handler
     * where it would be answered with a 500 and logged at ERROR on every malformed request.
     *
     * <p>The parser's own message is deliberately not echoed back: it can quote internal class
     * names and fragments of the offending payload.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Rejected unreadable request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST",
                        "That request could not be understood. Check the fields and try again."));
    }

    /** A path variable or query parameter of the wrong type. Also client error. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "INVALID_PARAMETER",
                        "The value supplied for '" + ex.getName() + "' is not valid."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        // Deliberately uniform: never reveal whether the username or the password was wrong.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHORIZED",
                        "Invalid username or password."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), "FORBIDDEN",
                        "You do not have permission to do that."));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        // Two writes hit the same account at once. Safe and expected under concurrent play.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "CONCURRENT_UPDATE",
                        "That request collided with another. Please try again."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                        "Something went wrong. Please try again."));
    }
}
