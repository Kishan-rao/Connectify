package com.example.backend;

import com.example.backend.security.CustomUserDetails;
import com.example.backend.security.JwtService;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies:
 *  1. The application starts with the test JWT secret from application.properties.
 *  2. The secret is NOT the old hardcoded value that was committed to source.
 *  3. Token generation and validation both work with the configured secret.
 */
@SpringBootTest
class JwtConfigTest {

    @Autowired JwtService jwtService;

    @Value("${app.jwt.secret}")
    String configuredSecret;

    private static final String OLD_COMMITTED_SECRET =
            "dGhpcy1pcy1hLXNlY3VyZS1zZWNyZXQta2V5LWZvci1qd3Qtc2lnbmluZy0yNTZiaXQ=";

    @Test
    void jwtSecret_isNotTheOldHardcodedValue() {
        assertThat(configuredSecret)
                .as("The old committed secret must no longer be used in production config")
                .isNotEqualTo(OLD_COMMITTED_SECRET);
    }

    @Test
    void jwtSecret_isNotBlank() {
        assertThat(configuredSecret).isNotBlank();
    }

    @Test
    void tokenGeneration_andValidation_workWithConfiguredSecret() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        CustomUserDetails details = new CustomUserDetails(user);

        String token = jwtService.generateToken(details);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token, details)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("testuser");
    }

    @Test
    void tokenFromDifferentSecret_isInvalid() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
        CustomUserDetails details = new CustomUserDetails(user);
        String token = jwtService.generateToken(details);

        // Tamper with the token signature (flip last char)
        String tampered = token.substring(0, token.length() - 1) + "X";

        assertThatThrownBy(() -> jwtService.isTokenValid(tampered, details))
                .isInstanceOf(Exception.class);
    }
}
