package com.casino.support;

import com.casino.domain.Role;
import com.casino.domain.UserAccount;
import com.casino.repository.LedgerEntryRepository;
import com.casino.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared plumbing for the API tests: a running context, an H2 database, and helpers for
 * obtaining tokens and posting JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected UserAccountRepository users;

    @Autowired
    protected LedgerEntryRepository ledger;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Starts a guest session and returns the parsed auth response. */
    protected JsonNode guestSession() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/guest")).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Registers a player and returns the parsed auth response. */
    protected JsonNode signUp(String username, String password) throws Exception {
        MvcResult result = mvc.perform(postJson("/api/auth/signup",
                """
                {"username":"%s","password":"%s"}
                """.formatted(username, password))).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    /**
     * Creates an admin directly in the database and signs in as them.
     *
     * <p>There is deliberately no endpoint that grants ADMIN: privilege is provisioned out of
     * band, so a test has to do the same.
     */
    protected JsonNode adminSession(String username, String password) throws Exception {
        UserAccount admin = new UserAccount(
                username, passwordEncoder.encode(password), Role.ADMIN, new BigDecimal("100.00"));
        users.save(admin);

        MvcResult result = mvc.perform(postJson("/api/auth/login",
                """
                {"username":"%s","password":"%s"}
                """.formatted(username, password))).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder postJson(String path, String body, String token) {
        return postJson(path, body).header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder getAs(String path, String token) {
        return get(path).header("Authorization", "Bearer " + token);
    }

    protected String token(JsonNode authResponse) {
        return authResponse.get("token").asText();
    }

    /** Performs a request and returns the parsed JSON body. */
    protected JsonNode perform(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isEmpty() ? json.createObjectNode() : json.readTree(body);
    }
}
