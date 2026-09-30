package com.example.backend;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostResponse;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.Post;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
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

@ExtendWith(MockitoExtension.class)
class PostServiceFeedTest {

    @Mock PostRepository postRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock GroupMembershipRepository groupMembershipRepository;
    @Mock GroupRepository groupRepository;
    @Mock PostLikeRepository postLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    PostService postService;
    UserService userService;

    User currentUser;
    User friendUser;
    User groupMemberUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, friendshipRepository, postRepository);
        postService = new PostService(postRepository, friendshipRepository, groupMembershipRepository,
                groupRepository, postLikeRepository, commentRepository, notificationRepository, userService, userRepository);

        currentUser = user("current");
        friendUser  = user("friend");
        groupMemberUser = user("groupmember");
    }

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
        UUID groupId = UUID.randomUUID();
        Group club = Group.builder()
                .id(groupId)
                .name("Photography Club")
                .type(GroupType.OPEN)
                .build();
        Post groupPost = post(groupMemberUser);
        groupPost.setGroup(club);

        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(friendshipRepository.findFriendIds(currentUser.getId())).thenReturn(List.of());
        when(groupMembershipRepository.findGroupIdsByUserId(currentUser.getId()))
                .thenReturn(List.of(groupId));
        var pageImpl = new PageImpl<>(List.of(groupPost), PageRequest.of(0, 20), 1);
        when(postRepository.findUnifiedFeed(any(), any(), any())).thenReturn(pageImpl);
        when(postLikeRepository.countLikesByPostIds(any())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(any())).thenReturn(List.of());
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(any(), any())).thenReturn(List.of());

        PagedResponse<PostResponse> feed = postService.getFeed(principalFor(currentUser), 0, 20);

        assertThat(feed.getContent()).hasSize(1);
        assertThat(feed.getContent().get(0).getFeedExplanation()).contains("Photography Club");
    }

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

        verify(friendshipRepository, never()).areFriends(any(), any());
        verify(friendshipRepository, times(1)).findFriendIds(currentUser.getId());
    }

    @Test
    void deletePost_ownerSucceeds() {
        Post ownPost = post(currentUser);
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(currentUser));
        when(postRepository.findById(ownPost.getId())).thenReturn(Optional.of(ownPost));

        assertThatNoException().isThrownBy(() ->
                postService.deletePost(principalFor(currentUser), ownPost.getId()));
        verify(notificationRepository).deleteByPost(ownPost);
        verify(commentRepository).deleteByPost(ownPost);
        verify(postLikeRepository).deleteByPost(ownPost);
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

    private void setupFeed(User current, List<UUID> friendIds, List<UUID> groupMemberIds,
                           Map<UUID, String> groupNameMap, List<Post> posts) {
        when(userRepository.findByUsernameOrEmail(any(), any())).thenReturn(Optional.of(current));
        when(friendshipRepository.findFriendIds(current.getId())).thenReturn(friendIds);
        when(groupMembershipRepository.findGroupIdsByUserId(current.getId())).thenReturn(List.of());

        var pageImpl = new PageImpl<>(posts, PageRequest.of(0, 20), posts.size());
        when(postRepository.findFeedByAuthorIds(any(), any())).thenReturn(pageImpl);
        when(postLikeRepository.countLikesByPostIds(any())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(any())).thenReturn(List.of());
        when(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(any(), any())).thenReturn(List.of());
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
