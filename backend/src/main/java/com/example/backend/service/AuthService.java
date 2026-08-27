package com.example.backend.service;

import com.example.backend.dto.AuthResponse;
import com.example.backend.dto.LoginRequest;
import com.example.backend.dto.RegisterRequest;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
import com.example.backend.security.CustomUserDetails;
import com.example.backend.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final NotificationRepository notificationRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final FriendshipRepository friendshipRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupRepository groupRepository;
    private final PostRepository postRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with this email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        User savedUser = Objects.requireNonNull(userRepository.save(user), "Saved user must not be null");
        CustomUserDetails userDetails = new CustomUserDetails(savedUser);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .role(savedUser.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()));

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication failed: no principal returned");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException(
                    "Unexpected principal type: " + principal.getClass().getName());
        }

        User user = userDetails.getUser();
        String token = jwtService.generateToken(userDetails);

        if (request.isRememberMe()) {
            refreshTokenService.createRefreshToken(user, response);
        } else {
            refreshTokenService.clearRefreshTokenCookie(response);
        }

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(userDetails.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        return login(request, null);
    }

    public AuthResponse refreshToken(String rawToken, HttpServletResponse response) {
        User user = refreshTokenService.rotateRefreshToken(rawToken, response);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtService.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    public void logout(String rawToken, HttpServletResponse response) {
        refreshTokenService.revokeRefreshToken(rawToken, response);
    }

    @Transactional
    public void deleteAccount(String username, String rawToken, HttpServletResponse response) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // 1. Delete refresh tokens
        refreshTokenService.deleteTokensForUser(user);

        // 2. Delete notifications
        notificationRepository.deleteByUserAsRecipientOrActor(user);
        notificationRepository.deleteByPostAuthor(user);
        notificationRepository.deleteByFriendshipUser(user);

        // 3. Delete comments
        commentRepository.deleteByUser(user);
        commentRepository.deleteByPostAuthor(user);

        // 4. Delete likes
        postLikeRepository.deleteByUser(user);
        postLikeRepository.deleteByPostAuthor(user);

        // 5. Delete friendships
        friendshipRepository.deleteByUserAsRequesterOrAddressee(user);

        // 6. Delete group memberships
        groupMembershipRepository.deleteByUser(user);
        groupMembershipRepository.deleteByGroupCreator(user);

        // 7. Delete posts
        postRepository.deleteByUser(user);
        postRepository.deleteByGroupCreator(user);

        // 8. Delete groups created by user
        groupRepository.deleteByCreatedBy(user);

        // 9. Delete user
        userRepository.delete(user);

        // 10. Clear cookies
        refreshTokenService.clearRefreshTokenCookie(response);
    }
}
