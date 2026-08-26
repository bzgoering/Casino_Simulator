package com.casino.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casino.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** The authorisation boundary: who can reach what, and what happens to a bad token. */
@Transactional
class SecurityApiTest extends ApiTestSupport {

    @Test
    @DisplayName("game endpoints reject a request with no token")
    void unauthenticatedRequestsRejected() throws Exception {
        mvc.perform(postJson("/api/games/slots/spin", """
                        {"bet": 10.00}
                        """))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a tampered token is rejected")
    void tamperedTokenRejected() throws Exception {
        String valid = token(guestSession());
        // Flip the final character of the signature.
        String tampered = valid.substring(0, valid.length() - 1)
                + (valid.endsWith("A") ? "B" : "A");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a token signed with the wrong key is rejected")
    void foreignTokenRejected() throws Exception {
        // A structurally valid HS256 token signed with a different secret.
        String foreign = io.jsonwebtoken.Jwts.builder()
                .issuer("casino-backend")
                .subject("attacker")
                .claim("role", "ADMIN")
                .claim("username", "attacker")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "an-entirely-different-signing-key-32-bytes".getBytes()))
                .compact();

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("garbage in the Authorization header is rejected, not crashed on")
    void garbageTokenRejected() throws Exception {
        for (String value : new String[]{"Bearer ", "Bearer not.a.token", "Basic abc", "xyz"}) {
            mvc.perform(get("/api/me").header("Authorization", value))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("a guest cannot reach the admin endpoints")
    void guestCannotReachAdmin() throws Exception {
        String token = token(guestSession());

        mvc.perform(postJson("/api/admin/credit/self", """
                        {"amount": 1000.00}
                        """, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ordinary player cannot reach the admin endpoints")
    void playerCannotReachAdmin() throws Exception {
        String token = token(signUp("normal_player", "correct-horse-9"));

        mvc.perform(postJson("/api/admin/credit/self", """
                        {"amount": 1000.00}
                        """, token))
                .andExpect(status().isForbidden());

        mvc.perform(getAs("/api/admin/audit", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a player cannot promote themselves by claiming a role at sign-up")
    void roleCannotBeSelfAssigned() throws Exception {
        // Extra fields are ignored: role is set by the server, never taken from the request.
        JsonNode result = perform(post("/api/auth/signup")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"sneaky_one","password":"correct-horse-9","role":"ADMIN","balance":999999}
                        """));

        assertThat(result.get("role").asText()).isEqualTo("PLAYER");
        assertThat(result.get("balance").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("an unknown path is denied rather than served")
    void unknownPathsDenied() throws Exception {
        String token = token(guestSession());

        mvc.perform(getAs("/api/secret", token))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("actuator exposes health only")
    void actuatorExposesHealthOnly() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // Everything else that would aid reconnaissance stays closed.
        mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());
        mvc.perform(get("/actuator/beans")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("security headers are set on API responses")
    void securityHeadersPresent() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-Frame-Options", "DENY"));
    }

    @Test
    @DisplayName("an error response never carries a stack trace or internal detail")
    void errorsDoNotLeakInternals() throws Exception {
        String token = token(guestSession());

        String body = perform(postJson("/api/games/slots/spin", """
                {"bet": 99999.00}
                """, token)).toString();

        assertThat(body)
                .doesNotContain("com.casino")
                .doesNotContain("Exception")
                .doesNotContain("at java.");
    }
}
