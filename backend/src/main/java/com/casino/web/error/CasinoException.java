package com.casino.web.error;

import org.springframework.http.HttpStatus;

/**
 * An error with a message that is safe to show the player.
 *
 * <p>Anything thrown as a {@code CasinoException} is expected and its text is returned verbatim.
 * Every other exception is reported to the client as a generic failure, so internal details never
 * leak through an error body.
 */
public class CasinoException extends RuntimeException {

    private final HttpStatus status;

    public CasinoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static CasinoException badRequest(String message) {
        return new CasinoException(HttpStatus.BAD_REQUEST, message);
    }

    public static CasinoException notFound(String message) {
        return new CasinoException(HttpStatus.NOT_FOUND, message);
    }

    public static CasinoException conflict(String message) {
        return new CasinoException(HttpStatus.CONFLICT, message);
    }

    public static CasinoException forbidden(String message) {
        return new CasinoException(HttpStatus.FORBIDDEN, message);
    }
}
