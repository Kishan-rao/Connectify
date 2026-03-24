package com.example.backend.dto;

import com.example.backend.entity.FriendshipStatus;
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
public class FriendshipResponse {
    private UUID id;
    private FriendshipStatus status;
    private UserSummaryDto requester;
    private UserSummaryDto addressee;
    private LocalDateTime createdAt;
}
