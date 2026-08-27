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
public class PostResponse {
    private UUID id;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
    private UserSummaryDto author;
    private String feedExplanation;
    private long likeCount;
    private long commentCount;
    private boolean likedByCurrentUser;
    private GroupSummaryDto group;
}
