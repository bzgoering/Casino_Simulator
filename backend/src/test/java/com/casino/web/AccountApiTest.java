package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casino.domain.Role;
import com.casino.domain.UserAccount;
import com.casino.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own account: what it discloses, and the two things they may do to it.
 *
 * <p>Both of those things are gated behind the account password rather than the bearer token
 * alone, which is what these tests are mostly checking.
 */
@Transactional
class AccountApiTest extends ApiTestSupport {

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("a player sees their UID, username and role")
        void playerSeesTheirDetails() throws Exception {
            JsonNode player = signUp("account_player", "correct-horse-9");

            JsonNode me = perform(getAs("/api/me", token(player)));

            assertThat(me.get("uid").asText()).isEqualTo(player.get("uid").asText());
            assertThat(me.get("username").asText()).isEqualTo("account_player");
            assertThat(me.get("role").asText()).isEqualTo("PLAYER");
            assertThat(me.get("guest").asBoolean()).isFalse();
            assertThat(me.get("balance").decimalValue()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("a guest sees their session id, which is what identifies them")
        void guestSeesTheirSessionId() throws Exception {
            JsonNode guest = guestSession();

            JsonNode me = perform(getAs("/api/me", token(guest)));

            assertThat(me.get("guest").asBoolean()).isTrue();
            assertThat(me.get("role").asText()).isEqualTo("GUEST");
            // The session id doubles as the UID: it is what an admin credits against.
            assertThat(me.get("uid").asText()).isEqualTo(guest.get("uid").asText());
            assertThat(me.get("uid").asText()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("changing a password")
    class ChangingPassword {

        @Test
        @DisplayName("a player can change their password and sign in with the new one")
        void playerCanChangePassword() throws Exception {
            JsonNode player = signUp("pw_player", "correct-horse-9");

            perform(postJson("/api/me/password", """
                    {"currentPassword":"correct-horse-9","newPassword":"staple-battery-7"}
                    """, token(player)));

            mvc.perform(postJson("/api/auth/login", """
                            {"username":"pw_player","password":"staple-battery-7"}
                            """))
                    .andExpect(status().isOk());
            // The old one stops working, or the change achieved nothing.
            mvc.perform(postJson("/api/auth/login", """
                            {"username":"pw_player","password":"correct-horse-9"}
                            """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the current password is required: a token alone cannot take an account over")
        void wrongCurrentPasswordRefused() throws Exception {
            JsonNode player = signUp("pw_wrong", "correct-horse-9");

            // Not a 401: the session is fine, the typed password is not. A 401 would make the
            // browser drop the token and sign the user out for one typo.
            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"not-my-password","newPassword":"staple-battery-7"}
                            """, token(player)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Current password is wrong."));

            // The token still works afterwards, which is the point.
            mvc.perform(getAs("/api/me", token(player))).andExpect(status().isOk());

            // The original password still works, so nothing was changed on the way through.
            mvc.perform(postJson("/api/auth/login", """
                            {"username":"pw_wrong","password":"correct-horse-9"}
                            """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a new password has to clear the same bar as one chosen at sign-up")
        void newPasswordMustMeetThePolicy() throws Exception {
            JsonNode player = signUp("pw_policy", "correct-horse-9");

            // Too short.
            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"correct-horse-9","newPassword":"short"}
                            """, token(player)))
                    .andExpect(status().isBadRequest());
            // A banned password.
            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"correct-horse-9","newPassword":"password123"}
                            """, token(player)))
                    .andExpect(status().isBadRequest());
            // Contains the username.
            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"correct-horse-9","newPassword":"xx_pw_policy_xx"}
                            """, token(player)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("reusing the current password is refused rather than quietly accepted")
        void reusingTheSamePasswordRefused() throws Exception {
            JsonNode player = signUp("pw_same", "correct-horse-9");

            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"correct-horse-9","newPassword":"correct-horse-9"}
                            """, token(player)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("New password must differ."));
        }

        @Test
        @DisplayName("a guest has no password to change")
        void guestHasNoPassword() throws Exception {
            String token = token(guestSession());

            mvc.perform(postJson("/api/me/password", """
                            {"currentPassword":"anything","newPassword":"staple-battery-7"}
                            """, token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Guests have no password."));
        }
    }

    @Nested
    @DisplayName("deleting an account")
    class DeletingAccount {

        @Test
        @DisplayName("a player can delete their account, and it stops existing")
        void playerCanDeleteTheirAccount() throws Exception {
            JsonNode player = signUp("gone_player", "correct-horse-9");

            JsonNode result = perform(postJson("/api/me/delete", """
                    {"password":"correct-horse-9"}
                    """, token(player)));

            assertThat(result.get("kind").asText()).isEqualTo("PLAYER");
            assertThat(users.findByUsernameIgnoreCase("gone_player")).isEmpty();

            // The old credentials no longer sign in, and the old token no longer resolves.
            mvc.perform(postJson("/api/auth/login", """
                            {"username":"gone_player","password":"correct-horse-9"}
                            """))
                    .andExpect(status().isUnauthorized());
            mvc.perform(getAs("/api/me", token(player)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("deleting takes the account's history with it")
        void deletingRemovesTheLedger() throws Exception {
            JsonNode player = signUp("ledger_gone", "correct-horse-9");
            perform(postJson("/api/games/slots/spin", """
                    {"bet": 1.00, "credits": 1}
                    """, token(player)));

            Long id = users.findByUsernameIgnoreCase("ledger_gone").orElseThrow().getId();
            assertThat(ledgerCountFor(id)).isPositive();

            perform(postJson("/api/me/delete", """
                    {"password":"correct-horse-9"}
                    """, token(player)));

            assertThat(users.findByUsernameIgnoreCase("ledger_gone")).isEmpty();
            assertThat(ledgerCountFor(id)).isZero();
        }

        @Test
        @DisplayName("the password is required, so a stray token cannot destroy an account")
        void wrongPasswordRefused() throws Exception {
            JsonNode player = signUp("keep_player", "correct-horse-9");

            mvc.perform(postJson("/api/me/delete", """
                            {"password":"not-my-password"}
                            """, token(player)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.message").value("Password is wrong."));

            // The session survives a wrong password, rather than being torn down with it.
            mvc.perform(getAs("/api/me", token(player))).andExpect(status().isOk());

            assertThat(users.findByUsernameIgnoreCase("keep_player")).isPresent();
        }

        @Test
        @DisplayName("a guest just ends their session, since nothing was ever stored")
        void guestSessionIsEnded() throws Exception {
            JsonNode guest = guestSession();

            JsonNode result = perform(postJson("/api/me/delete", "{}", token(guest)));

            assertThat(result.get("kind").asText()).isEqualTo("GUEST");
            // The session is gone, so the token that named it resolves to nothing.
            mvc.perform(getAs("/api/me", token(guest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the last admin cannot delete themselves and lock everyone out")
        void lastAdminIsProtected() throws Exception {
            JsonNode admin = adminSession("only_admin", "correct-horse-9");

            mvc.perform(postJson("/api/me/delete", """
                            {"password":"correct-horse-9"}
                            """, token(admin)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("The last admin cannot be deleted."));

            assertThat(users.findByUsernameIgnoreCase("only_admin")).isPresent();
        }

        @Test
        @DisplayName("an admin can delete themselves once another admin remains")
        void adminCanLeaveWhenAnotherRemains() throws Exception {
            JsonNode admin = adminSession("leaving_admin", "correct-horse-9");
            // A second admin, so administration survives the departure.
            users.save(new UserAccount("staying_admin",
                    passwordEncoder.encode("correct-horse-9"), Role.ADMIN, new BigDecimal("100.00")));

            perform(postJson("/api/me/delete", """
                    {"password":"correct-horse-9"}
                    """, token(admin)));

            assertThat(users.findByUsernameIgnoreCase("leaving_admin")).isEmpty();
            assertThat(users.findByUsernameIgnoreCase("staying_admin")).isPresent();
        }

        @Test
        @DisplayName("one player cannot delete another: the account comes from the token")
        void deletionIsScopedToTheCaller() throws Exception {
            JsonNode victim = signUp("victim_player", "correct-horse-9");
            JsonNode attacker = signUp("attacker_player", "correct-horse-9");

            // Both share a password, so only the token decides whose account is closed.
            perform(postJson("/api/me/delete", """
                    {"password":"correct-horse-9"}
                    """, token(attacker)));

            assertThat(users.findByUsernameIgnoreCase("attacker_player")).isEmpty();
            assertThat(users.findByUsernameIgnoreCase("victim_player")).isPresent();
            mvc.perform(getAs("/api/me", token(victim))).andExpect(status().isOk());
        }
    }

    private long ledgerCountFor(Long userId) {
        return ledger.findByUserIdOrderByCreatedAtDescIdDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 100)).size();
    }
}
