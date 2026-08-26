package com.casino.web.controller;

import com.casino.service.AuthService;
import com.casino.web.dto.AuthRequests;
import com.casino.web.dto.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up, sign-in and guest entry.
 *
 * <p>These are the only unauthenticated write endpoints, so they sit behind
 * {@code AuthRateLimitFilter}.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Registers a player and returns a token. Their opening balance is 100.00. */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signUp(@Valid @RequestBody AuthRequests.SignUpRequest request) {
        var result = authService.signUp(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(result));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequests.LoginRequest request) {
        return AuthResponse.from(authService.signIn(request.username(), request.password()));
    }

    /**
     * Starts an anonymous session with 10,000.00 in play money. Nothing about the guest is
     * written to the database; the session lives in memory until it goes idle.
     */
    @PostMapping("/guest")
    public AuthResponse guest() {
        return AuthResponse.from(authService.signInAsGuest());
    }
}
