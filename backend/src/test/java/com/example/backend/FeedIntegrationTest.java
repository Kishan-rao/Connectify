package com.example.backend;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostResponse;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full Spring Boot integration test (H2 in-memory).
 * Verifies that the feed includes own posts, friend posts, and posts from
 * groups the viewer belongs to, without leaking a group member's personal posts.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FeedIntegrationTest {

    @Autowired PostService postService;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, carol, dave;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob   = saveUser("bob");
        carol = saveUser("carol");
        dave  = saveUser("dave");
    }

    @Test
    void ownPost_appearsInFeed_withOwnExplanation() {
        savePost(alice, "Hello from Alice");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("your own post");
    }

    @Test
    void friendPost_appearsInFeed_withFriendExplanation() {
        makeFriends(alice, bob);
        savePost(bob, "Hello from Bob");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        List<PostResponse> posts = feed.getContent();
        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).getAuthor().getUsername()).isEqualTo("bob");
        assertThat(posts.get(0).getFeedExplanation()).contains("friend");
    }

    @Test
    void groupMemberPersonalPost_doesNotAppearInFeed() {
        Group club = saveGroup("BookClub");
        joinGroup(alice, club);
        joinGroup(carol, club);
        savePost(carol, "Hello from Carol");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        assertThat(feed.getContent()).isEmpty();
    }

    @Test
    void groupPost_appearsInFeed_withGroupExplanation() {
        Group club = saveGroup("CookingClub");
        joinGroup(alice, club);
        joinGroup(carol, club);
        saveGroupPost(carol, club, "Hello from the group");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getAuthor().getUsername()).isEqualTo("carol");
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("CookingClub");
    }

    @Test
    void strangerPost_doesNotAppearInFeed() {
        savePost(dave, "Hello from Dave");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        assertThat(feed.getContent()).isEmpty();
    }

    @Test
    void friendWhoIsAlsoGroupMember_onlyAppearsOnce_withFriendExplanation() {
        Group club = saveGroup("PhotoClub");
        joinGroup(alice, club);
        joinGroup(bob, club);
        makeFriends(alice, bob);
        savePost(bob, "Bob is friend and group member");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        // Post must appear exactly ONCE
        assertThat(feed.getContent()).hasSize(1);
        // Friend relationship takes priority over shared group
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("friend");
    }

    @Test
    void feed_isPagedCorrectly() {
        makeFriends(alice, bob);
        for (int i = 0; i < 5; i++) savePost(bob, "Post " + i);

        PagedResponse<PostResponse> page0 = postService.getFeed(principal(alice), 0, 3);
        PagedResponse<PostResponse> page1 = postService.getFeed(principal(alice), 1, 3);

        assertThat(page0.getContent()).hasSize(3);
        assertThat(page0.getTotalElements()).isEqualTo(5L);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page1.getContent()).hasSize(2);
    }

    @Test
    void feed_noDuplicatePosts_whenUserInMultipleGroups() {
        Group club1 = saveGroup("Club1");
        Group club2 = saveGroup("Club2");
        joinGroup(alice, club1);
        joinGroup(alice, club2);
        joinGroup(carol, club1);
        joinGroup(carol, club2);
        saveGroupPost(carol, club1, "Carol in two clubs");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
    }

    // ── Privacy-leak regression tests (feed must only show p.group IS NULL) ──

    /**
     * Scenario 1: viewer belongs to zero groups.
     * Friend's personal post must appear; friend's PRIVATE-group post must NOT.
     */
    @Test
    void privacyLeak_scenario1_viewerInNoGroups_privateGroupPostHidden() {
        // viewer = alice, friend = bob
        makeFriends(alice, bob);

        // Bob creates a personal post
        savePost(bob, "Bob personal post");

        // Bob creates a post inside a PRIVATE group that alice never joins
        Group privateGroup = savePrivateGroup("SecretGroup1");
        joinGroup(bob, privateGroup);
        saveGroupPost(bob, privateGroup, "Bob private group post");

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        List<PostResponse> posts = feed.getContent();
        // Personal post must appear
        assertThat(posts).anyMatch(p -> p.getContent().equals("Bob personal post"));
        // Private-group post must NOT appear (alice is not a member)
        assertThat(posts).noneMatch(p -> p.getContent().equals("Bob private group post"));
    }

    /**
     * Scenario 2: viewer belongs to a different group (Group B).
     * Friend's post in Group A (PRIVATE, viewer not a member) must NOT appear.
     */
    @Test
    void privacyLeak_scenario2_viewerInDifferentGroup_groupAPostHidden() {
        // viewer = alice, friend = bob
        makeFriends(alice, bob);

        // Group A: PRIVATE — bob posts here, alice is NOT a member
        Group groupA = savePrivateGroup("GroupA_Private");
        joinGroup(bob, groupA);
        saveGroupPost(bob, groupA, "Bob post in Group A");

        // Group B: alice is a member (bob is not)
        Group groupB = saveGroup("GroupB_Open");
        joinGroup(alice, groupB);

        PagedResponse<PostResponse> feed = postService.getFeed(principal(alice), 0, 20);

        // Group A post must NOT appear — alice is not a member of Group A
        assertThat(feed.getContent())
                .noneMatch(p -> p.getContent().equals("Bob post in Group A"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .role(Role.USER)
                .build());
    }

    private void savePost(User author, String content) {
        postRepository.save(Post.builder().user(author).content(content).build());
    }

    private void saveGroupPost(User author, Group group, String content) {
        postRepository.save(Post.builder().user(author).group(group).content(content).build());
    }

    private void makeFriends(User a, User b) {
        friendshipRepository.save(Friendship.builder()
                .requester(a).addressee(b).status(FriendshipStatus.ACCEPTED).build());
    }

    private Group saveGroup(String name) {
        return groupRepository.save(Group.builder().name(name).type(GroupType.OPEN).build());
    }

    private Group savePrivateGroup(String name) {
        return groupRepository.save(Group.builder().name(name).type(GroupType.PRIVATE).build());
    }

    private void joinGroup(User user, Group group) {
        groupMembershipRepository.save(GroupMembership.builder().user(user).group(group).build());
    }

    private static Principal principal(User u) {
        return u::getUsername;
    }
}
