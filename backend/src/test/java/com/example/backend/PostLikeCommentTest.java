package com.example.backend;

import com.example.backend.dto.CommentCreateRequest;
import com.example.backend.dto.CommentResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.*;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
import com.example.backend.service.CommentService;
import com.example.backend.service.PostLikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PostLikeCommentTest {

    @Autowired PostLikeService postLikeService;
    @Autowired CommentService commentService;
    @Autowired UserRepository userRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostLikeRepository postLikeRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, charlie;
    Post post;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob   = saveUser("bob");
        charlie = saveUser("charlie");
        post = postRepository.save(Post.builder().user(alice).content("Alice post").build());
    }

    @Test
    void userCanLikeAndUnlikePost() {
        postLikeService.likePost(principal(bob), post.getId());
        assertThat(postLikeService.getLikeCount(post.getId())).isEqualTo(1L);

        List<UserSummaryDto> likes = postLikeService.getLikes(post.getId());
        assertThat(likes).hasSize(1);
        assertThat(likes.get(0).getUsername()).isEqualTo("bob");

        postLikeService.unlikePost(principal(bob), post.getId());
        assertThat(postLikeService.getLikeCount(post.getId())).isEqualTo(0L);
    }

    @Test
    void duplicateLikeIsIdempotentAndPrevented() {
        postLikeService.likePost(principal(bob), post.getId());
        postLikeService.likePost(principal(bob), post.getId());

        assertThat(postLikeService.getLikeCount(post.getId())).isEqualTo(1L);
        assertThat(postLikeRepository.findAll()).hasSize(1);
    }

    @Test
    void cannotLikeNonExistentPost_throws404() {
        UUID nonExistent = UUID.randomUUID();
        assertThatThrownBy(() -> postLikeService.likePost(principal(bob), nonExistent))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userCanCreateComment() {
        CommentResponse comment = commentService.createComment(
                principal(bob),
                post.getId(),
                new CommentCreateRequest("Great post Alice!")
        );

        assertThat(comment.getContent()).isEqualTo("Great post Alice!");
        assertThat(comment.getUser().getUsername()).isEqualTo("bob");
        assertThat(comment.getPostId()).isEqualTo(post.getId());

        List<CommentResponse> comments = commentService.getComments(principal(bob), post.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).getContent()).isEqualTo("Great post Alice!");
    }

    @Test
    void commentOwnerCanDeleteComment() {
        CommentResponse comment = commentService.createComment(
                principal(bob),
                post.getId(),
                new CommentCreateRequest("Comment to delete")
        );

        commentService.deleteComment(principal(bob), comment.getId());
        assertThat(commentRepository.findById(comment.getId())).isEmpty();
    }

    @Test
    void nonCommentOwnerCannotDeleteComment_throws403() {
        CommentResponse comment = commentService.createComment(
                principal(bob),
                post.getId(),
                new CommentCreateRequest("Bob comment")
        );

        assertThatThrownBy(() -> commentService.deleteComment(principal(charlie), comment.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(commentRepository.findById(comment.getId())).isPresent();
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
