package com.casino.web.controller;

import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.casino.security.CasinoPrincipal;
import com.casino.security.CurrentUser;
import com.casino.service.WalletService;
import com.casino.web.dto.AccountResponses;
import com.casino.web.error.CasinoException;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in caller's own profile and history. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private static final int MAX_HISTORY = 100;

    private final WalletService wallet;
    private final UserAccountRepository users;
    private final LedgerEntryRepository ledger;

    public AccountController(WalletService wallet, UserAccountRepository users, LedgerEntryRepository ledger) {
        this.wallet = wallet;
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
            return new AccountResponses.HistoryResponse(java.util.List.of());
        }
        var account = users.findByUid(principal.subject())
                .orElseThrow(() -> CasinoException.notFound("Account not found."));

        int capped = Math.clamp(limit, 1, MAX_HISTORY);
        var entries = ledger
                .findByUserIdOrderByCreatedAtDescIdDesc(account.getId(), PageRequest.of(0, capped))
                .stream()
                .map(AccountResponses.LedgerEntryView::from)
                .toList();
        return new AccountResponses.HistoryResponse(entries);
    }
}
