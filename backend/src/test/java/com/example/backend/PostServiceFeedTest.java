package com.example.backend;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostResponse;
import com.example.backend.entity.Post;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.PostService;
import com.example.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PostService feed logic.
 * Key goals:
 *  - No per-post repository calls (N+1 prevention)
 *  - Correct explanation labels
 *  - Pagination passes through correctly
 *  - Correct 403 on delete of another user's post
 *  - Correct 404 on delete of non-existent post
 */
@ExtendWith(MockitoExtension.class)
class PostServiceFeedTest {

    @Mock PostRepository postRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock GroupMembershipRepository groupMembershipRepository;
    @Mock UserRepository userRepository;

    PostService postService;
    UserService userService;

    User currentUser;
    User friendUser;
    User groupMemberUser;
    User strangerUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, friendshipRepository, postRepository);
        postService = new PostService(postRepository, friendshipRepository, groupMembershipRepository, userService, userRepository);

        currentUser = user("current");
        friendUser  = user("friend");
        groupMemberUser = user("groupmember");
        strangerUser = user("stranger");
    }

    // ── Feed explanation correctness ──────────────────────────────────────────

    @Test
    void ownPost_explanation_isOwnPost() {
        setupFeed(currentUser, List.of(), List.of(), Map.of(),
                List.of(post(currentUser)));

        PagedResponse<PostResponse> feed = postService.getFeed(principalFor(currentUser), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("your own post");
    }

    @Test
    void friendPost_explanation_isFriend() {
        setupFeed(currentUser,
                List.of(friendUser.getId()),
                List.of(),
                Map.of(),
                List.of(post(friendUser)));

        PagedResponse<PostResponse> feed = postService.getFeed(principalFor(currentUser), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("friend");
    }

    @Test
    void groupMemberPost_explanation_isGroup() {
        UUID gmId = groupMemberUser.getId();
        setupFeed(currentUser,
                List.of(),
                List.of(gmId),
                Map.of(gmId, "Photography Club"),
                List.of(post(groupMemberUser)));

        PagedResponse<PostResponse> feed = postService.getFeed(principalFor(currentUser), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("Photography Club");
    }

    // ── No N+1: bulk queries called exactly once ──────────────────────────────

    @Test
    void feedDoesNotCallAreFriendsPerPost() {
        Post p1 = post(friendUser);
        Post p2 = post(friendUser);
        Post p3 = post(friendUser);

        setupFeed(currentUser,
                List.of(friendUser.getId()),
                List.of(),
                Map.of(),
                List.of(p1, p2, p3));

        postService.getFeed(principalFor(currentUser), 0, 20);

        // areFriends should NEVER be called — we use pre-loaded friendIds
        verify(friendshipRepository, never()).areFriends(any(), any());
        // findFriendIds should be called exactly once (bulk)
        verify(friendshipRepository, times(1)).findFriendIds(currentUser.getId());
    }

    @Test
    void feedDoesNotCallFindMutualGroupsPerPost() {
        Post p1 = post(groupMemberUser);
        Post p2 = post(groupMemberUser);

        UUID gmId = groupMemberUser.getId();
        setupFeed(currentUser, List.of(), List.of(gmId), Map.of(gmId, "Art Club"), List.of(p1, p2));

        postService.getFeed(principalFor(currentUser), 0, 20);

        // findMutualGroups should NEVER be called per-post
        verify(groupMembershipRepository, never()).findMutualGroups(any(), any());
        // findGroupMemberUserIds should be called exactly once (bulk)
        verify(groupMembershipRepository, times(1)).findGroupMemberUserIds(currentUser.getId());
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    void pagination_metadataPassesThrough() {
        List<Post> posts = List.of(post(currentUser));
        var pageImpl = new PageImpl<>(posts, PageRequest.of(2, 5), 30);

        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(friendshipRepository.findFriendIds(currentUser.getId())).thenReturn(List.of());
        when(groupMembershipRepository.findGroupMemberUserIds(currentUser.getId())).thenReturn(List.of());
        when(postRepository.findFeedByAuthorIds(any(), any())).thenReturn(pageImpl);

        PagedResponse<PostResponse> feed = postService.getFeed(principalFor(currentUser), 2, 5);

        assertThat(feed.getPage()).isEqualTo(2);
        assertThat(feed.getSize()).isEqualTo(5);
        assertThat(feed.getTotalElements()).isEqualTo(30L);
        assertThat(feed.getTotalPages()).isEqualTo(6);
    }

    // ── Post authorization ────────────────────────────────────────────────────

    @Test
    void deletePost_ownerSucceeds() {
        Post ownPost = post(currentUser);
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(ownPost.getId())).thenReturn(Optional.of(ownPost));

        assertThatNoException().isThrownBy(() ->
                postService.deletePost(principalFor(currentUser), ownPost.getId()));
        verify(postRepository).delete(ownPost);
    }

    @Test
    void deletePost_nonOwnerReceives403() {
        Post friendPost = post(friendUser);
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(friendPost.getId())).thenReturn(Optional.of(friendPost));

        assertThatThrownBy(() -> postService.deletePost(principalFor(currentUser), friendPost.getId()))
                .isInstanceOf(AccessDeniedException.class);
        verify(postRepository, never()).delete(any());
    }

    @Test
    void deletePost_nonExistentPostReceives404() {
        UUID randomId = UUID.randomUUID();
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(principalFor(currentUser), randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getUserPosts — unknown user ───────────────────────────────────────────

    @Test
    void getUserPosts_unknownUser_throws404() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getUserPosts("ghost", 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setupFeed(User current, List<UUID> friendIds, List<UUID> groupMemberIds,
                           Map<UUID, String> groupNameMap, List<Post> posts) {
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(current));
        when(friendshipRepository.findFriendIds(current.getId())).thenReturn(friendIds);
        when(groupMembershipRepository.findGroupMemberUserIds(current.getId())).thenReturn(groupMemberIds);

        if (!groupMemberIds.isEmpty()) {
            List<Object[]> rows = new ArrayList<>();
            groupNameMap.forEach((uid, name) -> rows.add(new Object[]{uid, name}));
            when(groupMembershipRepository.findSharedGroupNamesByUserIds(eq(current.getId()), any()))
                    .thenReturn(rows);
        }

        var pageImpl = new PageImpl<>(posts, PageRequest.of(0, 20), posts.size());
        when(postRepository.findFeedByAuthorIds(any(), any())).thenReturn(pageImpl);
    }

    private static User user(String username) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build();
    }

    private static Post post(User author) {
        Post p = new Post();
        p.setId(UUID.randomUUID());
        p.setUser(author);
        p.setContent("Post by " + author.getUsername());
        return p;
    }

    private static Principal principalFor(User u) {
        return u::getUsername;
    }
}
