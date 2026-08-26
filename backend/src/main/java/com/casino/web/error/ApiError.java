package com.casino.web.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single error shape returned by every endpoint.
 *
 * @param status     HTTP status code
 * @param error      short machine-readable code
 * @param message    text safe to display to the player
 * @param fieldErrors per-field validation messages, when the failure was a bad request body
 * @param timestamp  when the error was produced
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, null, Instant.now());
    }

    public static ApiError validation(int status, String message, Map<String, String> fieldErrors) {
        return new ApiError(status, "VALIDATION_FAILED", message, fieldErrors, Instant.now());
    }
}
