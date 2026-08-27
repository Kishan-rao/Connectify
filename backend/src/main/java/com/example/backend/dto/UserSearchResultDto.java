package com.example.backend.dto;

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
public class UserSearchResultDto {
    private UUID id;
    private String username;
    private LocalDateTime createdAt;
    private long friendCount;
    private String relationshipStatus; // NONE, FRIENDS, PENDING_SENT, PENDING_RECEIVED, SELF
}
