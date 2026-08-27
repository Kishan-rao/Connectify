package com.example.backend.service;

import com.example.backend.dto.PublicUserProfileResponse;
import com.example.backend.dto.UserProfileResponse;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;

    /** Returns the authenticated user's own profile including email. */
    public UserProfileResponse getMyProfile(Principal principal) {
        User user = resolveUser(principal.getName());
        return buildPrivateProfile(user);
    }

    /** Returns a public profile view that does NOT include email. */
    public PublicUserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return buildPublicProfile(user);
    }

    private UserProfileResponse buildPrivateProfile(User user) {
        long friendCount = friendshipRepository.findAllAcceptedFriendships(user).size();
        long postCount = postRepository.countByUser(user);
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .friendCount(friendCount)
                .postCount(postCount)
                .build();
    }

    private PublicUserProfileResponse buildPublicProfile(User user) {
        long friendCount = friendshipRepository.findAllAcceptedFriendships(user).size();
        long postCount = postRepository.countByUser(user);
        return PublicUserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .createdAt(user.getCreatedAt())
                .friendCount(friendCount)
                .postCount(postCount)
                .build();
    }

    /**
     * Resolves the authenticated principal's username/email to a User entity.
     * Throws {@link UsernameNotFoundException} (Spring Security contract) rather
     * than {@link ResourceNotFoundException} so that the JWT filter and
     * authentication machinery continue to work correctly.
     */
    public User resolveUser(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
    }
}

