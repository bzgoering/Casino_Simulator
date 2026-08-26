package com.casino.web.dto;

import com.casino.service.AuthService;
import java.math.BigDecimal;

/**
 * What the browser receives after signing in.
 *
 * @param token    bearer token for subsequent calls
 * @param uid      the public identifier; for a guest this is the session id
 * @param username display name
 * @param role     GUEST, PLAYER or ADMIN
 * @param balance  starting balance for this session
 */
public record AuthResponse(String token, String uid, String username, String role, BigDecimal balance) {

    public static AuthResponse from(AuthService.AuthResult result) {
        return new AuthResponse(
                result.token(),
                result.principal().subject(),
                result.principal().username(),
                result.principal().role().name(),
                result.balance());
    }
}
