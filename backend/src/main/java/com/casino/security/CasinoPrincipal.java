package com.casino.security;

import com.casino.domain.Role;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller, covering both persisted accounts and anonymous guests.
 *
 * <p>{@code subject} is the account UID for a registered user and the guest session id for a
 * guest. Everything downstream keys off this one type, so a controller never has to care which
 * kind of caller it is serving.
 *
 * @param subject  account UID, or guest session id
 * @param username display name; guests are simply "guest"
 * @param role     the tier this caller has
 */
public record CasinoPrincipal(String subject, String username, Role role) {

    public boolean isGuest() {
        return role == Role.GUEST;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }
}
