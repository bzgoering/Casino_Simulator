package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casino.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AuthApiTest extends ApiTestSupport {

    @Test
    @DisplayName("a guest gets 10,000 in play money and is never written to the database")
    void guestStartsWithTenThousand() throws Exception {
        long accountsBefore = users.count();

        JsonNode guest = guestSession();

        assertThat(guest.get("role").asText()).isEqualTo("GUEST");
        assertThat(guest.get("balance").decimalValue()).isEqualByComparingTo("10000.00");
        assertThat(guest.get("token").asText()).isNotBlank();
        // The defining property of a guest: no row was created for them.
        assertThat(users.count()).isEqualTo(accountsBefore);
    }

    @Test
    @DisplayName("a new player gets 100, a UID, and a token")
    void signUpGrantsOpeningBalance() throws Exception {
        JsonNode player = signUp("alice_smith", "correct-horse-9");

        assertThat(player.get("role").asText()).isEqualTo("PLAYER");
        assertThat(player.get("balance").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(player.get("uid").asText()).hasSize(36); // a UUID, not a row id
        assertThat(player.get("username").asText()).isEqualTo("alice_smith");
    }

    @Test
    @DisplayName("the UID is a random UUID, so registration order is not leaked")
    void uidIsNotSequential() throws Exception {
        String first = signUp("player_one", "correct-horse-9").get("uid").asText();
        String second = signUp("player_two", "correct-horse-9").get("uid").asText();

        assertThat(first).isNotEqualTo(second);
        assertThat(java.util.UUID.fromString(first)).isNotNull();
        assertThat(java.util.UUID.fromString(second)).isNotNull();
    }

    @Test
    @DisplayName("a duplicate username is refused")
    void duplicateUsernameRefused() throws Exception {
        signUp("bob_jones", "correct-horse-9");

        mvc.perform(postJson("/api/auth/signup",
                        """
                        {"username":"bob_jones","password":"correct-horse-9"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That username is already taken."));
    }

    @Test
    @DisplayName("usernames are compared case-insensitively, so BOB cannot shadow bob")
    void duplicateUsernameIsCaseInsensitive() throws Exception {
        signUp("charlie", "correct-horse-9");

        mvc.perform(postJson("/api/auth/signup",
                        """
                        {"username":"CHARLIE","password":"correct-horse-9"}
                        """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a short password is refused")
    void shortPasswordRefused() throws Exception {
        mvc.perform(postJson("/api/auth/signup",
                        """
                        {"username":"dave_x","password":"short"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a password containing the username is refused")
    void passwordContainingUsernameRefused() throws Exception {
        mvc.perform(postJson("/api/auth/signup",
                        """
                        {"username":"eve_smith","password":"eve_smith_2024"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password must not contain your username."));
    }

    @Test
    @DisplayName("an invalid username shape is refused")
    void invalidUsernameRefused() throws Exception {
        mvc.perform(postJson("/api/auth/signup",
                        """
                        {"username":"has spaces!","password":"correct-horse-9"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a correct password signs the player in")
    void signInSucceeds() throws Exception {
        signUp("frank_l", "correct-horse-9");

        mvc.perform(postJson("/api/auth/login",
                        """
                        {"username":"frank_l","password":"correct-horse-9"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    @DisplayName("a wrong password and an unknown user give the same answer")
    void failedSignInsAreIndistinguishable() throws Exception {
        signUp("grace_h", "correct-horse-9");

        String wrongPassword = perform(postJson("/api/auth/login",
                """
                {"username":"grace_h","password":"wrong-password-1"}
                """)).toString();
        String unknownUser = perform(postJson("/api/auth/login",
                """
                {"username":"nobody_here","password":"wrong-password-1"}
                """)).toString();

        // Identical wording: the response must not disclose whether the account exists.
        assertThat(wrongPassword).contains("Invalid username or password.");
        assertThat(unknownUser).contains("Invalid username or password.");
    }

    @Test
    @DisplayName("repeated failures lock the account")
    void repeatedFailuresLockTheAccount() throws Exception {
        signUp("henry_k", "correct-horse-9");

        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(postJson("/api/auth/login",
                            """
                            {"username":"henry_k","password":"wrong-password-1"}
                            """))
                    .andExpect(status().isUnauthorized());
        }

        // The sixth attempt is refused even though the password is now correct.
        mvc.perform(postJson("/api/auth/login",
                        """
                        {"username":"henry_k","password":"correct-horse-9"}
                        """))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("the password hash is never returned to the client")
    void hashIsNeverReturned() throws Exception {
        String body = perform(post("/api/auth/signup")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"ivan_p","password":"correct-horse-9"}
                        """)).toString();

        assertThat(body).doesNotContain("passwordHash").doesNotContain("$2a$");
    }
}
