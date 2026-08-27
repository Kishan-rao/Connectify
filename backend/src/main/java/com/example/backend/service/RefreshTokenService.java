package com.example.backend.service;

import com.example.backend.entity.RefreshToken;
import com.example.backend.entity.User;
import com.example.backend.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@SuppressWarnings("null")
public class RefreshTokenService {

    public static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshToken createRefreshToken(User user, HttpServletResponse response) {
        String rawToken = generateSecureRandomToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plus(REFRESH_TOKEN_VALIDITY))
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        setRefreshTokenCookie(response, rawToken, REFRESH_TOKEN_VALIDITY);
        return saved;
    }

    public User rotateRefreshToken(String rawToken, HttpServletResponse response) {
        if (rawToken == null || rawToken.isBlank()) {
            clearRefreshTokenCookie(response);
            throw new BadCredentialsException("Refresh token is missing or empty.");
        }

        String tokenHash = hashToken(rawToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            clearRefreshTokenCookie(response);
            throw new BadCredentialsException("Invalid refresh token.");
        }

        RefreshToken token = tokenOpt.get();

        // Detect reuse of revoked token (potential security threat)
        if (token.getRevokedAt() != null) {
            // Revoke all tokens for this user to protect compromised account
            refreshTokenRepository.revokeAllForUser(token.getUser(), LocalDateTime.now());
            clearRefreshTokenCookie(response);
            throw new BadCredentialsException("Refresh token has already been revoked.");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            clearRefreshTokenCookie(response);
            throw new BadCredentialsException("Refresh token has expired.");
        }

        // Revoke the old token (rotation)
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        User user = token.getUser();
        // Issue brand new refresh token and cookie
        createRefreshToken(user, response);

        return user;
    }

    public void revokeRefreshToken(String rawToken, HttpServletResponse response) {
        if (rawToken != null && !rawToken.isBlank()) {
            String tokenHash = hashToken(rawToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
                if (t.getRevokedAt() == null) {
                    t.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(t);
                }
            });
        }
        clearRefreshTokenCookie(response);
    }

    public void deleteTokensForUser(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String rawToken, Duration maxAge) {
        if (response == null) return;
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(false) // Set to true in HTTPS environments
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        if (response == null) return;
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String generateSecureRandomToken() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
