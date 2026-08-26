package com.casino.domain;

public enum LedgerEntryType {
    /** Opening balance granted at sign-up. */
    SIGNUP_GRANT,
    /** Stake removed from the balance when a wager is committed. */
    BET,
    /** Winnings returned, including the stake on a win or push. */
    PAYOUT,
    /** Balance added by an administrator. */
    ADMIN_CREDIT
}
