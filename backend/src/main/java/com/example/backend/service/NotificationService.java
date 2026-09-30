package com.example.backend.service;

import com.example.backend.dto.NotificationResponse;
import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Friendship;
import com.example.backend.entity.Notification;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public Notification createNotification(User recipient, User actor, NotificationType type,
                                           Post post, Friendship friendship, String customMessage) {
        if (recipient == null || actor == null) return null;
        // Do not notify a user about their own actions
        if (recipient.getId().equals(actor.getId())) return null;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .post(post)
                .friendship(friendship)
                .message(customMessage)
                .read(false)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getMyNotifications(Principal principal, int page, int size) {
        User user = userService.resolveUser(principal.getName());
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> pageResult = notificationRepository.findByRecipientOrderByCreatedAtDesc(user, pageable);

        List<NotificationResponse> content = pageResult.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.<NotificationResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    public void markAsRead(Principal principal, UUID notificationId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredId = Objects.requireNonNull(notificationId, "notificationId must not be null");
        Notification notification = notificationRepository.findById(requiredId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + requiredId));

        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to modify this notification.");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        notificationRepository.markAllAsReadForRecipient(user);
    }

    private NotificationResponse toResponse(Notification n) {
        String displayMessage = n.getMessage();
        if (displayMessage == null || displayMessage.isBlank()) {
            displayMessage = generateMessage(n);
        }

        return NotificationResponse.builder()
                .id(n.getId())
                .actor(new UserSummaryDto(n.getActor().getId(), n.getActor().getUsername()))
                .type(n.getType())
                .postId(n.getPost() != null ? n.getPost().getId() : null)
                .friendshipId(n.getFriendship() != null ? n.getFriendship().getId() : null)
                .message(displayMessage)
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private String generateMessage(Notification n) {
        String actorName = "@" + n.getActor().getUsername();
        return switch (n.getType()) {
            case FRIEND_REQUEST -> actorName + " sent you a friend request.";
            case FRIEND_REQUEST_ACCEPTED -> actorName + " accepted your friend request.";
            case POST_LIKED -> actorName + " liked your post.";
            case POST_COMMENTED -> actorName + " commented on your post.";
            case GROUP_INVITE -> actorName + " invited you to join a group.";
        };
    }
}
