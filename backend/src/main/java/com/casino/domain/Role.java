package com.casino.domain;

/**
 * Account tiers.
 *
 * <p>{@link #GUEST} never reaches the database. Guest play is held in a short-lived in-memory
 * session so that, as required, no data is kept on guests. The constant exists so authorities
 * and balance defaults can be expressed uniformly across the codebase.
 */
public enum Role {

    /** Anonymous play money. Nothing is persisted; the session evaporates when it expires. */
    GUEST("10000.00"),

    /** Registered, persisted account. */
    PLAYER("100.00"),

    /** Player privileges plus the ability to credit balances. */
    ADMIN("100.00");

    private final String startingBalance;

    Role(String startingBalance) {
        this.startingBalance = startingBalance;
    }

    public String startingBalance() {
        return startingBalance;
    }

    /** Spring Security convention: authorities are prefixed with {@code ROLE_}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
