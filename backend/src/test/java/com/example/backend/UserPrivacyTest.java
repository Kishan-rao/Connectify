package com.example.backend;

import com.example.backend.dto.PublicUserProfileResponse;
import com.example.backend.dto.UserProfileResponse;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that the public profile endpoint does NOT expose email,
 * while the private /me endpoint still includes it.
 */
@ExtendWith(MockitoExtension.class)
class UserPrivacyTest {

    @Mock UserRepository userRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock PostRepository postRepository;

    UserService userService;

    User alice;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, friendshipRepository, postRepository);
        alice = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
    }

    // ── Public profile ────────────────────────────────────────────────────────

    @Test
    void publicProfile_doesNotContainEmail() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(friendshipRepository.countAcceptedFriendships(alice)).thenReturn(0L);
        when(postRepository.countByUser(alice)).thenReturn(0L);

        PublicUserProfileResponse response = userService.getUserProfile("alice");

        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getId()).isEqualTo(alice.getId());
        // PublicUserProfileResponse has no getEmail() — compile-time guarantee.
        // We also verify the return type is NOT UserProfileResponse (which has email).
        assertThat(response).isInstanceOf(PublicUserProfileResponse.class);
        assertThat(response).isNotInstanceOf(UserProfileResponse.class);
    }

    @Test
    void publicProfile_nonExistentUser_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile("ghost"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ── Private /me profile ───────────────────────────────────────────────────

    @Test
    void privateProfile_containsEmail() {
        Principal principal = () -> "alice";
        when(userRepository.findByUsernameOrEmail("alice", "alice")).thenReturn(Optional.of(alice));
        when(friendshipRepository.countAcceptedFriendships(alice)).thenReturn(0L);
        when(postRepository.countByUser(alice)).thenReturn(3L);

        UserProfileResponse response = userService.getMyProfile(principal);

        assertThat(response.getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getPostCount()).isEqualTo(3L);
    }

    // ── User friends list ─────────────────────────────────────────────────────

    @Test
    void getUserFriends_returnsSummaryWithoutEmail() {
        User bob = User.builder().id(UUID.randomUUID()).username("bob").email("bob@example.com").build();
        com.example.backend.entity.Friendship friendship = com.example.backend.entity.Friendship.builder()
                .requester(alice)
                .addressee(bob)
                .status(com.example.backend.entity.FriendshipStatus.ACCEPTED)
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(friendshipRepository.findAllAcceptedFriendships(alice)).thenReturn(List.of(friendship));

        List<com.example.backend.dto.UserSummaryDto> friends = userService.getUserFriends("alice");
        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).getUsername()).isEqualTo("bob");
        assertThat(friends.get(0).getId()).isEqualTo(bob.getId());
    }
}
