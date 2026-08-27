package com.example.backend;

import com.example.backend.dto.CommentCreateRequest;
import com.example.backend.dto.FriendRequestDto;
import com.example.backend.dto.FriendshipResponse;
import com.example.backend.dto.NotificationResponse;
import com.example.backend.dto.PagedResponse;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.CommentService;
import com.example.backend.service.FriendshipService;
import com.example.backend.service.NotificationService;
import com.example.backend.service.PostLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class NotificationServiceTest {

    @Autowired NotificationService notificationService;
    @Autowired FriendshipService friendshipService;
    @Autowired PostLikeService postLikeService;
    @Autowired CommentService commentService;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob;
    Post alicePost;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob   = saveUser("bob");
        alicePost = postRepository.save(Post.builder().user(alice).content("Alice post").build());
    }

    @Test
    void friendRequest_createsNotificationForAddressee() {
        friendshipService.sendRequest(principal(bob), new FriendRequestDto("alice"));

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(alice), 0, 10);
        assertThat(notifs.getContent()).hasSize(1);
        assertThat(notifs.getContent().get(0).getType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(notifs.getContent().get(0).getActor().getUsername()).isEqualTo("bob");
        assertThat(notifs.getContent().get(0).getMessage()).contains("sent you a friend request");
    }

    @Test
    void friendAccept_createsNotificationForRequester() {
        FriendshipResponse req = friendshipService.sendRequest(principal(bob), new FriendRequestDto("alice"));
        friendshipService.respondToRequest(principal(alice), req.getId(), true);

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(bob), 0, 10);
        assertThat(notifs.getContent()).hasSize(1);
        assertThat(notifs.getContent().get(0).getType()).isEqualTo(NotificationType.FRIEND_REQUEST_ACCEPTED);
        assertThat(notifs.getContent().get(0).getActor().getUsername()).isEqualTo("alice");
    }

    @Test
    void likePost_createsNotificationForAuthor() {
        postLikeService.likePost(principal(bob), alicePost.getId());

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(alice), 0, 10);
        assertThat(notifs.getContent()).hasSize(1);
        assertThat(notifs.getContent().get(0).getType()).isEqualTo(NotificationType.POST_LIKED);
        assertThat(notifs.getContent().get(0).getActor().getUsername()).isEqualTo("bob");
    }

    @Test
    void commentPost_createsNotificationForAuthor() {
        commentService.createComment(principal(bob), alicePost.getId(), new CommentCreateRequest("Nice post!"));

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(alice), 0, 10);
        assertThat(notifs.getContent()).hasSize(1);
        assertThat(notifs.getContent().get(0).getType()).isEqualTo(NotificationType.POST_COMMENTED);
        assertThat(notifs.getContent().get(0).getActor().getUsername()).isEqualTo("bob");
    }

    @Test
    void selfLikeOrComment_doesNotCreateNotification() {
        postLikeService.likePost(principal(alice), alicePost.getId());
        commentService.createComment(principal(alice), alicePost.getId(), new CommentCreateRequest("Self comment"));

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(alice), 0, 10);
        assertThat(notifs.getContent()).isEmpty();
    }

    @Test
    void markAsRead_andUnreadCount() {
        postLikeService.likePost(principal(bob), alicePost.getId());
        assertThat(notificationService.getUnreadCount(principal(alice))).isEqualTo(1L);

        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(alice), 0, 10);
        NotificationResponse n = notifs.getContent().get(0);

        notificationService.markAsRead(principal(alice), n.getId());
        assertThat(notificationService.getUnreadCount(principal(alice))).isEqualTo(0L);
    }

    @Test
    void userCannotSeeAnotherUsersNotifications() {
        postLikeService.likePost(principal(bob), alicePost.getId());

        PagedResponse<NotificationResponse> bobNotifs = notificationService.getMyNotifications(principal(bob), 0, 10);
        assertThat(bobNotifs.getContent()).isEmpty();
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
