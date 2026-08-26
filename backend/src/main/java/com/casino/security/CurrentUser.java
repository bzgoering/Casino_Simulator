package com.casino.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the caller in the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** The authenticated caller, or empty when the request carried no usable token. */
    public static java.util.Optional<CasinoPrincipal> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CasinoPrincipal principal)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(principal);
    }

    /** The authenticated caller, or a 401-mapped failure when there is none. */
    public static CasinoPrincipal require() {
        return get().orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                "Authentication is required"));
    }
}
