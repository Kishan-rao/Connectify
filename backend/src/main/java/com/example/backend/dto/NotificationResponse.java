package com.example.backend.dto;

import com.example.backend.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private UserSummaryDto actor;
    private NotificationType type;
    private UUID postId;
    private UUID friendshipId;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
}
