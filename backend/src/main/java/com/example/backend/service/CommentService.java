package com.example.backend.service;

import com.example.backend.dto.CommentCreateRequest;
import com.example.backend.dto.CommentResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Comment;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CommentRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@SuppressWarnings("null")
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserService userService;
    private final GroupMembershipRepository groupMembershipRepository;
    private final NotificationService notificationService;

    public CommentResponse createComment(Principal principal, UUID postId, CommentCreateRequest request) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));

        validateGroupPostAccess(user, post);

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(request.getContent().trim())
                .build();
        Comment saved = commentRepository.save(comment);

        // Send notification to post author
        notificationService.createNotification(
                post.getUser(),
                user,
                NotificationType.POST_COMMENTED,
                post,
                null,
                null
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Principal principal, UUID postId) {
        User user = principal != null ? userService.resolveUser(principal.getName()) : null;
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));

        if (user != null) {
            validateGroupPostAccess(user, post);
        }

        return commentRepository.findByPostOrderByCreatedAtAsc(post).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void deleteComment(Principal principal, UUID commentId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredCommentId = Objects.requireNonNull(commentId, "commentId must not be null");
        Comment comment = commentRepository.findById(requiredCommentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + requiredCommentId));

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only delete your own comments.");
        }

        commentRepository.delete(comment);
    }

    private void validateGroupPostAccess(User user, Post post) {
        Group group = post.getGroup();
        if (group != null && (group.getType() == GroupType.PRIVATE || group.getType() == GroupType.CLOSED)) {
            if (!groupMembershipRepository.existsByGroupAndUser(group, user)) {
                throw new AccessDeniedException("You do not have permission to access posts in this private group.");
            }
        }
    }

    private CommentResponse toResponse(Comment c) {
        return CommentResponse.builder()
                .id(c.getId())
                .postId(c.getPost().getId())
                .user(new UserSummaryDto(c.getUser().getId(), c.getUser().getUsername()))
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
