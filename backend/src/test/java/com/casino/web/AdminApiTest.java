package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("an admin can change one game's limits, and they bind on that game only")
    void adminCanChangeOneGamesLimits() throws Exception {
        JsonNode admin = adminSession("admin_limits", "correct-horse-9");
        JsonNode player = signUp("limits_player", "correct-horse-9");

        try {
            JsonNode limits = perform(postJson("/api/admin/limits", """
                    {"game":"ROULETTE","minBet": 5.00, "maxBet": 50.00}
                    """, token(admin)));

            assertThat(limits.get("games").get("ROULETTE").get("minBet").decimalValue())
                    .isEqualByComparingTo("5.00");
            assertThat(limits.get("games").get("ROULETTE").get("maxBet").decimalValue())
                    .isEqualByComparingTo("50.00");
            // Blackjack is untouched by a change to the roulette table.
            assertThat(limits.get("games").get("BLACKJACK").get("minBet").decimalValue())
                    .isEqualByComparingTo("1.00");

            // The public config reports what is actually enforced, per game.
            JsonNode config = perform(get("/api/config"));
            assertThat(config.get("roulette").get("minBet").decimalValue()).isEqualByComparingTo("5.00");
            assertThat(config.get("blackjack").get("minBet").decimalValue()).isEqualByComparingTo("1.00");

            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"COLOR","selection":"RED","amount":2.00}]}
                            """, token(player)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Below 5.00 minimum."));

            mvc.perform(postJson("/api/games/roulette/spin", """
                            {"bets":[{"type":"COLOR","selection":"RED","amount":80.00}]}
                            """, token(player)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Above 50.00 maximum."));

            // The same stake is still fine at the blackjack table.
            mvc.perform(postJson("/api/games/blackjack/deal", """
                            {"bet": 2.00}
                            """, token(player)))
                    .andExpect(status().isOk());

            // And the slot machine is not governed by these limits at all.
            mvc.perform(postJson("/api/games/slots/spin", """
                            {"bet": 0.05, "credits": 1}
                            """, token(player)))
                    .andExpect(status().isOk());
        } finally {
            // The validator holds the limits outside the transaction, so put them back.
            perform(postJson("/api/admin/limits", """
                    {"game":"ROULETTE","minBet": 1.00, "maxBet": 5000.00}
                    """, token(admin)));
        }
    }

    @Test
    @DisplayName("only the table games appear in the limits, since slots are not a table game")
    void limitsCoverTableGamesOnly() throws Exception {
        JsonNode admin = adminSession("admin_read_limits", "correct-horse-9");

        JsonNode limits = perform(getAs("/api/admin/limits", token(admin)));

        assertThat(limits.get("games").has("BLACKJACK")).isTrue();
        assertThat(limits.get("games").has("ROULETTE")).isTrue();
        assertThat(limits.get("games").has("SLOTS")).isFalse();
        assertThat(limits.get("maxConfigurableBet").decimalValue()).isPositive();
    }

    @Test
    @DisplayName("slot limits cannot be set at all: a machine is not a table game")
    void slotLimitsAreRefused() throws Exception {
        JsonNode admin = adminSession("admin_slots_limits", "correct-horse-9");

        mvc.perform(postJson("/api/admin/limits", """
                        {"game":"SLOTS","minBet": 5.00, "maxBet": 50.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not a table game."));
    }

    @Test
    @DisplayName("a maximum below the minimum is refused")
    void invertedLimitsRefused() throws Exception {
        JsonNode admin = adminSession("admin_inverted", "correct-horse-9");

        mvc.perform(postJson("/api/admin/limits", """
                        {"game":"ROULETTE","minBet": 100.00, "maxBet": 10.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Maximum below minimum."));
    }

    @Test
    @DisplayName("the maximum bet cannot be raised past the configured ceiling")
    void limitsCannotExceedTheCeiling() throws Exception {
        JsonNode admin = adminSession("admin_ceiling", "correct-horse-9");

        mvc.perform(postJson("/api/admin/limits", """
                        {"game":"BLACKJACK","minBet": 1.00, "maxBet": 99999999.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("limits cannot be set on something that is not a game at all")
    void limitsRejectNonGames() throws Exception {
        JsonNode admin = adminSession("admin_nongame", "correct-horse-9");

        mvc.perform(postJson("/api/admin/limits", """
                        {"game":"ACCOUNT","minBet": 1.00, "maxBet": 10.00}
                        """, token(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not a table game."));
    }

    @Test
    @DisplayName("a player cannot change any game's limits")
    void playerCannotChangeTableLimits() throws Exception {
        JsonNode player = signUp("nosy_player", "correct-horse-9");

        mvc.perform(postJson("/api/admin/limits", """
                        {"game":"SLOTS","minBet": 0.01, "maxBet": 999999.00}
                        """, token(player)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("history reports lifetime play totals, excluding the sign-up grant")
    void historyReportsPlayTotals() throws Exception {
        JsonNode player = signUp("totals_player", "correct-horse-9");

        BigDecimal wagered = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            JsonNode spin = perform(postJson("/api/games/slots/spin", """
                    {"bet": 2.00}
                    """, token(player)));
            wagered = wagered.add(new BigDecimal("2.00"));
            returned = returned.add(spin.get("payout").decimalValue());
        }

        JsonNode totals = perform(getAs("/api/me/history", token(player))).get("totals");

        assertThat(totals.get("wagered").decimalValue()).isEqualByComparingTo(wagered);
        assertThat(totals.get("returned").decimalValue()).isEqualByComparingTo(returned);
        assertThat(totals.get("net").decimalValue()).isEqualByComparingTo(returned.subtract(wagered));
    }
}
