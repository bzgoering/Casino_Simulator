package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casino.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminApiTest extends ApiTestSupport {

    @Test
    @DisplayName("an admin can credit their own balance")
    void adminCanCreditSelf() throws Exception {
        JsonNode admin = adminSession("admin_one", "correct-horse-9");

        JsonNode result = perform(postJson("/api/admin/credit/self", """
                {"amount": 5000.00}
                """, token(admin)));

        assertThat(result.get("newBalance").decimalValue()).isEqualByComparingTo("5100.00");
        assertThat(result.get("targetKind").asText()).isEqualTo("PLAYER");
    }

    @Test
    @DisplayName("an admin can credit another player by their UID")
    void adminCanCreditPlayerByUid() throws Exception {
        JsonNode admin = adminSession("admin_two", "correct-horse-9");
        JsonNode player = signUp("target_player", "correct-horse-9");
        String targetUid = player.get("uid").asText();

        JsonNode result = perform(postJson("/api/admin/credit", """
                {"targetUid":"%s","amount":250.00}
                """.formatted(targetUid), token(admin)));

        assertThat(result.get("targetUid").asText()).isEqualTo(targetUid);
        assertThat(result.get("targetUsername").asText()).isEqualTo("target_player");
        assertThat(result.get("newBalance").decimalValue()).isEqualByComparingTo("350.00");

        // The credited player sees it on their own account.
        JsonNode me = perform(getAs("/api/me", token(player)));
        assertThat(me.get("balance").decimalValue()).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("an admin can credit a live guest session by its id")
    void adminCanCreditGuest() throws Exception {
        JsonNode admin = adminSession("admin_three", "correct-horse-9");
        JsonNode guest = guestSession();
        String guestId = guest.get("uid").asText();

        JsonNode result = perform(postJson("/api/admin/credit", """
                {"targetUid":"%s","amount":500.00}
                """.formatted(guestId), token(admin)));

        assertThat(result.get("targetKind").asText()).isEqualTo("GUEST");
        assertThat(result.get("newBalance").decimalValue()).isEqualByComparingTo("10500.00");

        JsonNode me = perform(getAs("/api/me", token(guest)));
        assertThat(me.get("balance").decimalValue()).isEqualByComparingTo("10500.00");
    }

    @Test
    @DisplayName("crediting an unknown UID is refused")
    void unknownUidRefused() throws Exception {
        JsonNode admin = adminSession("admin_four", "correct-horse-9");

        mvc.perform(postJson("/api/admin/credit", """
                        {"targetUid":"00000000-0000-0000-0000-000000000000","amount":100.00}
                        """, token(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a credit above the configured ceiling is refused")
    void oversizedCreditRefused() throws Exception {
        JsonNode admin = adminSession("admin_five", "correct-horse-9");

        mvc.perform(postJson("/api/admin/credit/self", """
                        {"amount": 99999999.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a negative credit is refused, so it cannot be used to drain an account")
    void negativeCreditRefused() throws Exception {
        JsonNode admin = adminSession("admin_six", "correct-horse-9");

        mvc.perform(postJson("/api/admin/credit/self", """
                        {"amount": -500.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("every credit is written to the audit log with actor and target")
    void creditsAreAudited() throws Exception {
        JsonNode admin = adminSession("admin_seven", "correct-horse-9");
        JsonNode player = signUp("audited_player", "correct-horse-9");

        perform(postJson("/api/admin/credit", """
                {"targetUid":"%s","amount":75.00}
                """.formatted(player.get("uid").asText()), token(admin)));

        JsonNode audit = perform(getAs("/api/admin/audit?limit=10", token(admin)));

        assertThat(audit).isNotEmpty();
        JsonNode entry = audit.get(0);
        assertThat(entry.get("actorUsername").asText()).isEqualTo("admin_seven");
        assertThat(entry.get("action").asText()).isEqualTo("CREDIT_BALANCE");
        assertThat(entry.get("targetRef").asText()).isEqualTo(player.get("uid").asText());
        assertThat(entry.get("amount").decimalValue()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("an admin retains ordinary player abilities")
    void adminCanStillPlay() throws Exception {
        JsonNode admin = adminSession("admin_eight", "correct-horse-9");

        JsonNode spin = perform(postJson("/api/games/slots/spin", """
                {"bet": 10.00}
                """, token(admin)));

        assertThat(spin.get("roundId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a player's history records their own money movements, and a guest has none")
    void historyIsRecordedForPlayersOnly() throws Exception {
        JsonNode player = signUp("history_player", "correct-horse-9");
        perform(postJson("/api/games/slots/spin", """
                {"bet": 10.00}
                """, token(player)));

        JsonNode history = perform(getAs("/api/me/history", token(player)));
        var entries = history.get("entries");

        // The welcome grant, the bet, and the payout if the spin won.
        assertThat(entries).isNotEmpty();
        assertThat(entries.toString()).contains("SIGNUP_GRANT").contains("BET");

        // A guest leaves no trail at all: that is what makes them a guest.
        JsonNode guestHistory = perform(getAs("/api/me/history", token(guestSession())));
        assertThat(guestHistory.get("entries")).isEmpty();
    }

    @Test
    @DisplayName("the ledger balance always matches the account balance")
    void ledgerReconcilesWithBalance() throws Exception {
        JsonNode player = signUp("ledger_player", "correct-horse-9");

        for (int i = 0; i < 5; i++) {
            perform(postJson("/api/games/slots/spin", """
                    {"bet": 2.00}
                    """, token(player)));
        }

        JsonNode me = perform(getAs("/api/me", token(player)));
        JsonNode history = perform(getAs("/api/me/history?limit=100", token(player)));

        BigDecimal ledgerTotal = BigDecimal.ZERO;
        for (JsonNode entry : history.get("entries")) {
            ledgerTotal = ledgerTotal.add(entry.get("amount").decimalValue());
        }

        assertThat(ledgerTotal).isEqualByComparingTo(me.get("balance").decimalValue());
    }
}
