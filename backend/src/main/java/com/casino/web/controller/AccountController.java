package com.casino.web.controller;

import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.security.CurrentUser;
import com.casino.service.AccountService;
import com.casino.service.WalletService;
import com.casino.web.dto.AccountRequests;
import com.casino.web.dto.AccountResponses;
import com.casino.web.error.CasinoException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in caller's own profile and history. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private static final int MAX_HISTORY = 100;

    private final WalletService wallet;
    private final AccountService accounts;
    private final UserAccountRepository users;
    private final LedgerEntryRepository ledger;

    public AccountController(WalletService wallet, AccountService accounts,
                             UserAccountRepository users, LedgerEntryRepository ledger) {
        this.wallet = wallet;
        this.accounts = accounts;
        this.users = users;
        this.ledger = ledger;
    }

    /** Who the caller is and what they currently hold. */
    @GetMapping
    public AccountResponses.MeResponse me() {
        CasinoPrincipal principal = CurrentUser.require();
        return new AccountResponses.MeResponse(
                principal.subject(),
                principal.username(),
                principal.role().name(),
                wallet.balanceOf(principal),
                principal.isGuest());
    }

    /**
     * Changes the caller's own password.
     *
     * <p>The current password is required as well as the new one: a token on its own must not be
     * enough to take an account over.
     */
    @PostMapping("/password")
    public AccountResponses.DeletedResponse changePassword(
            @Valid @RequestBody AccountRequests.ChangePasswordRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        accounts.changePassword(principal, request.currentPassword(), request.newPassword());
        return new AccountResponses.DeletedResponse("PASSWORD", principal.username(),
                "Your password has been changed.");
    }

    /**
     * Closes the caller's own account.
     *
     * <p>Scoped to the caller by their token, so nobody can delete anyone else by changing a
     * parameter, and gated behind the account password so a stray token cannot do it either.
     */
    @PostMapping("/delete")
    public AccountResponses.DeletedResponse deleteAccount(
            @Valid @RequestBody AccountRequests.DeleteAccountRequest request) {
        CasinoPrincipal principal = CurrentUser.require();
        var result = accounts.deleteAccount(principal, request.password());
        return new AccountResponses.DeletedResponse(result.kind(), result.username(),
                result.kind().equals("GUEST")
                        ? "Your guest session has been ended. Nothing about it was ever stored."
                        : "Your account and its history have been deleted.");
    }

    /**
     * Recent money movements on the caller's own account.
     *
     * <p>Scoped to the caller by their token, never by an id in the request, so one player cannot
     * read another's history by changing a parameter.
     */
    @GetMapping("/history")
    public AccountResponses.HistoryResponse history(
            @RequestParam(defaultValue = "25") int limit) {
        CasinoPrincipal principal = CurrentUser.require();
        if (principal.isGuest()) {
            // Guests have no stored history; that is the point of being a guest.
            return new AccountResponses.HistoryResponse(java.util.List.of(),
                    AccountResponses.PlayTotals.none());
        }
        var account = users.findByUid(principal.subject())
                .orElseThrow(() -> CasinoException.notFound("Account not found."));

        int capped = Math.clamp(limit, 1, MAX_HISTORY);
        var entries = ledger
                .findByUserIdOrderByCreatedAtDescIdDesc(account.getId(), PageRequest.of(0, capped))
                .stream()
                .map(AccountResponses.LedgerEntryView::from)
                .toList();
        java.math.BigDecimal[] totals = ledger.playTotals(account.getId());
        return new AccountResponses.HistoryResponse(entries,
                AccountResponses.PlayTotals.of(totals[0], totals[1]));
    }
}
