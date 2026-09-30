package com.example.backend.service;

import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.Post;
import com.example.backend.entity.PostLike;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.PostLikeRepository;
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
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final UserService userService;
    private final GroupMembershipRepository groupMembershipRepository;
    private final NotificationService notificationService;

    public void likePost(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));

        validateGroupPostAccess(user, post);

        if (!postLikeRepository.existsByPostAndUser(post, user)) {
            PostLike like = PostLike.builder()
                    .post(post)
                    .user(user)
                    .build();
            postLikeRepository.save(like);

            // Send notification to post author
            notificationService.createNotification(
                    post.getUser(),
                    user,
                    NotificationType.POST_LIKED,
                    post,
                    null,
                    null
            );
        }
    }

    public void unlikePost(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));

        postLikeRepository.findByPostAndUser(post, user)
                .ifPresent(postLikeRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getLikes(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));

        validateGroupPostAccess(user, post);

        return postLikeRepository.findByPostOrderByCreatedAtDesc(post).stream()
                .map(pl -> new UserSummaryDto(pl.getUser().getId(), pl.getUser().getUsername()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getLikeCount(UUID postId) {
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));
        return postLikeRepository.countByPost(post);
    }

    private void validateGroupPostAccess(User user, Post post) {
        Group group = post.getGroup();
        if (group != null && (group.getType() == GroupType.PRIVATE || group.getType() == GroupType.CLOSED)) {
            if (!groupMembershipRepository.existsByGroupAndUser(group, user)) {
                throw new AccessDeniedException("You do not have permission to access posts in this private group.");
            }
        }
    }
}
