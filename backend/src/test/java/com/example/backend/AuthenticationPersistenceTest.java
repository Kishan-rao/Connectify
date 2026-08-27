package com.example.backend;

import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.PostCreateRequest;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.User;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.RefreshTokenRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.AuthService;
import com.example.backend.service.PostService;
import com.example.backend.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthenticationPersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PostService postService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testRegistration_PersistsHashedPassword_NeverStoresPlaintext() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("persistuser");
        registerReq.setEmail("persist@example.com");
        registerReq.setPassword("Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("persistuser"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Optional<User> userOpt = userRepository.findByUsername("persistuser");
        assertTrue(userOpt.isPresent());
        User user = userOpt.get();

        assertNotEquals("Password123!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2a$") || user.getPasswordHash().startsWith("$2b$"));

        // Duplicate username fails with 409 Conflict
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void testLogin_ValidAndInvalid_AccountPersists() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("loginuser");
        registerReq.setEmail("loginuser@example.com");
        registerReq.setPassword("Password123!");
        authService.register(registerReq);

        // Invalid password -> 401
        LoginRequest invalidLogin = LoginRequest.builder()
                .usernameOrEmail("loginuser")
                .password("WrongPassword")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidLogin)))
                .andExpect(status().isUnauthorized());

        // Account still exists
        assertTrue(userRepository.findByUsername("loginuser").isPresent());

        // Valid login -> 200
        LoginRequest validLogin = LoginRequest.builder()
                .usernameOrEmail("loginuser")
                .password("Password123!")
                .rememberMe(false)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        // Refresh cookie should either be absent or cleared (maxAge == 0)
        Cookie cookie = result.getResponse().getCookie("refreshToken");
        assertTrue(cookie == null || cookie.getValue().isEmpty() || cookie.getMaxAge() == 0);

        // Account still exists in DB
        assertTrue(userRepository.findByUsername("loginuser").isPresent());
    }

    @Test
    void testRememberMe_IssuesRefreshTokenInCookieAndRotatesSuccessfully() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("rememberuser");
        registerReq.setEmail("remember@example.com");
        registerReq.setPassword("Password123!");
        authService.register(registerReq);

        LoginRequest loginReq = LoginRequest.builder()
                .usernameOrEmail("rememberuser")
                .password("Password123!")
                .rememberMe(true)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        jakarta.servlet.http.Cookie cookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(cookie);
        String rawRefreshToken = cookie.getValue();
        assertFalse(rawRefreshToken.isBlank());

        // Verify token in DB is stored as SHA-256 hash
        String tokenHash = refreshTokenService.hashToken(rawRefreshToken);
        Optional<RefreshToken> dbTokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        assertTrue(dbTokenOpt.isPresent());
        assertNull(dbTokenOpt.get().getRevokedAt());

        // Refresh with cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn();

        // Old token should now be revoked
        Optional<RefreshToken> oldTokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
        assertTrue(oldTokenOpt.isPresent());
        assertNotNull(oldTokenOpt.get().getRevokedAt());

        // New token cookie is issued
        Cookie newCookie = refreshResult.getResponse().getCookie("refreshToken");
        assertNotNull(newCookie);
        assertNotEquals(rawRefreshToken, newCookie.getValue());
    }

    @Test
    void testRefreshToken_RevokedOrInvalid_Returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-fake-token-123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testLogout_RevokesTokenAndClearsCookie_PreservesAccount() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("logoutuser");
        registerReq.setEmail("logout@example.com");
        registerReq.setPassword("Password123!");
        authService.register(registerReq);

        LoginRequest loginReq = LoginRequest.builder()
                .usernameOrEmail("logoutuser")
                .password("Password123!")
                .rememberMe(true)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(cookie);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookie))
                .andExpect(status().isOk());

        // Token is revoked
        String tokenHash = refreshTokenService.hashToken(cookie.getValue());
        Optional<RefreshToken> dbToken = refreshTokenRepository.findByTokenHash(tokenHash);
        assertTrue(dbToken.isPresent());
        assertNotNull(dbToken.get().getRevokedAt());

        // User is NOT deleted
        assertTrue(userRepository.findByUsername("logoutuser").isPresent());
    }

    @Test
    void testDeleteAccount_TransactionallyDeletesUserAndAllAssociatedData() throws Exception {
        RegisterRequest registerReq = new RegisterRequest();
        registerReq.setUsername("deleteme");
        registerReq.setEmail("deleteme@example.com");
        registerReq.setPassword("Password123!");
        var authRes = authService.register(registerReq);
        String token = authRes.getToken();

        // Create a post
        PostCreateRequest postReq = new PostCreateRequest();
        postReq.setContent("Post by user to be deleted");
        postService.createPost(() -> "deleteme", postReq);

        // Login with remember me to create a refresh token
        LoginRequest loginReq = LoginRequest.builder()
                .usernameOrEmail("deleteme")
                .password("Password123!")
                .rememberMe(true)
                .build();
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();
        Cookie cookie = loginResult.getResponse().getCookie("refreshToken");

        // Execute account deletion
        mockMvc.perform(delete("/api/auth/account")
                        .header("Authorization", "Bearer " + token)
                        .cookie(cookie))
                .andExpect(status().isOk());

        // User should be completely removed
        assertFalse(userRepository.findByUsername("deleteme").isPresent());

        // Cannot login anymore
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());
    }
}
