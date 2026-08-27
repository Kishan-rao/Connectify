package com.example.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies backend HTTP status semantics that the Axios interceptor fix depends on.
 *
 * The frontend axiosClient.js fix ensures auth-endpoint 401s are NOT intercepted
 * for redirect.  These tests confirm the backend sends the right status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AxiosInterceptorBehaviorTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Login errors ──────────────────────────────────────────────────────────

    @Test
    void login_badCredentials_returns401_withJsonBody() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("usernameOrEmail", "nobody", "password", "wrongpassword"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                // Must be JSON error body, not a redirect
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void register_duplicateUsername_returns409_notRedirect() throws Exception {
        // Register once successfully
        String body = objectMapper.writeValueAsString(
                Map.of("username", "dupuser", "email", "dup@example.com", "password", "Password1"));
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

        // Register again with same username → must get 409, not redirect
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ── Protected endpoint without token ─────────────────────────────────────

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/feed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/feed")
                        .header("Authorization", "Bearer this.is.not.valid"))
                .andExpect(status().isUnauthorized());
    }
}
