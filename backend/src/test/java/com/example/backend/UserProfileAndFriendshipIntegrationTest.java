package com.example.backend;

import com.example.backend.dto.*;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.FriendshipService;
import com.example.backend.service.GroupService;
import com.example.backend.service.PostService;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserProfileAndFriendshipIntegrationTest {

    @Autowired UserService userService;
    @Autowired PostService postService;
    @Autowired FriendshipService friendshipService;
    @Autowired GroupService groupService;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, charlie;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob   = saveUser("bob");
        charlie = saveUser("charlie");
    }

    @Test
    void profile_returnsUserPosts() {
        postService.createPost(principal(alice), PostCreateRequest.builder().content("Alice first post").build());
        postService.createPost(principal(alice), PostCreateRequest.builder().content("Alice second post").build());
        postService.createPost(principal(alice), PostCreateRequest.builder().content("Alice third post").build());

        // Alice views own posts
        PagedResponse<PostResponse> ownPosts = postService.getUserPosts(principal(alice), "alice", 0, 20);
        assertThat(ownPosts.getContent()).hasSize(3);
        assertThat(ownPosts.getContent()).allMatch(p -> p.getAuthor().getUsername().equals("alice"));

        // Bob views Alice's posts
        PagedResponse<PostResponse> bobViewsAlice = postService.getUserPosts(principal(bob), "alice", 0, 20);
        assertThat(bobViewsAlice.getContent()).hasSize(3);
        assertThat(bobViewsAlice.getContent()).allMatch(p -> p.getAuthor().getUsername().equals("alice"));
    }

    @Test
    void profile_returnsCorrectFriendCount() {
        // Establish friendship: bob -> alice
        FriendshipResponse fr1 = friendshipService.sendRequest(principal(bob), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), fr1.getId(), true);

        // Establish friendship: charlie -> alice
        FriendshipResponse fr2 = friendshipService.sendRequest(principal(charlie), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), fr2.getId(), true);

        // Alice public profile has friendCount = 2
        PublicUserProfileResponse alicePublic = userService.getUserProfile("alice");
        assertThat(alicePublic.getFriendCount()).isEqualTo(2L);

        // Alice private profile (/me) has friendCount = 2
        UserProfileResponse alicePrivate = userService.getMyProfile(principal(alice));
        assertThat(alicePrivate.getFriendCount()).isEqualTo(2L);

        // Bob public profile has friendCount = 1
        PublicUserProfileResponse bobPublic = userService.getUserProfile("bob");
        assertThat(bobPublic.getFriendCount()).isEqualTo(1L);
    }

    @Test
    void friendList_canBeRetrievedForUser_andContainsExpectedUsers() {
        // Bob -> Alice accepted
        FriendshipResponse fr1 = friendshipService.sendRequest(principal(bob), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), fr1.getId(), true);

        // Charlie -> Alice accepted
        FriendshipResponse fr2 = friendshipService.sendRequest(principal(charlie), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), fr2.getId(), true);

        // Retrieve Alice's friends via UserService
        List<UserSummaryDto> userFriends = userService.getUserFriends("alice");
        assertThat(userFriends).hasSize(2);
        List<String> usernames = userFriends.stream().map(UserSummaryDto::getUsername).toList();
        assertThat(usernames).containsExactlyInAnyOrder("bob", "charlie");

        // Retrieve Alice's friends via FriendshipService
        List<UserSummaryDto> friendshipFriends = friendshipService.listUserFriends("alice");
        assertThat(friendshipFriends).hasSize(2);
        List<String> friendshipUsernames = friendshipFriends.stream().map(UserSummaryDto::getUsername).toList();
        assertThat(friendshipUsernames).containsExactlyInAnyOrder("bob", "charlie");
    }

    @Test
    void friendList_doesNotExposeEmailAddresses() {
        FriendshipResponse fr = friendshipService.sendRequest(principal(bob), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), fr.getId(), true);

        List<UserSummaryDto> friends = userService.getUserFriends("alice");
        assertThat(friends).hasSize(1);
        UserSummaryDto bobSummary = friends.get(0);

        // UserSummaryDto only has id and username; no email field
        assertThat(bobSummary.getUsername()).isEqualTo("bob");
        assertThat(bobSummary.getId()).isEqualTo(bob.getId());
        assertThat(bobSummary.getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().equalsIgnoreCase("email"));
    }

    @Test
    void userPosts_respectsGroupPrivacyForNonMembers() {
        // Alice creates a private group
        GroupResponse privateGroup = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("AliceSecretGroup", "Secret", GroupType.PRIVATE)
        );

        // Alice creates a personal post and a group post
        postService.createPost(principal(alice), PostCreateRequest.builder().content("Personal post").build());
        postService.createPost(principal(alice), PostCreateRequest.builder()
                .content("Secret group post")
                .groupId(privateGroup.getId())
                .build());

        // Alice sees both in her posts
        PagedResponse<PostResponse> aliceView = postService.getUserPosts(principal(alice), "alice", 0, 20);
        assertThat(aliceView.getContent()).hasSize(2);

        // Bob (non-member) only sees the personal post
        PagedResponse<PostResponse> bobView = postService.getUserPosts(principal(bob), "alice", 0, 20);
        assertThat(bobView.getContent()).hasSize(1);
        assertThat(bobView.getContent().get(0).getContent()).isEqualTo("Personal post");
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .role(Role.USER)
                .build());
    }

    private static Principal principal(User u) {
        return u::getUsername;
    }
}
