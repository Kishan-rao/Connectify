package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Public-facing user profile. Does NOT include email or other private fields.
 * Returned by GET /api/users/{username} (viewing another user's profile).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileResponse {
    private UUID id;
    private String username;
    private LocalDateTime createdAt;
    private long friendCount;
    private long postCount;
    private String relationshipStatus; // NONE, FRIENDS, PENDING_SENT, PENDING_RECEIVED, SELF
    private UUID friendshipId;          // Present when PENDING_RECEIVED to allow accept/decline
}
