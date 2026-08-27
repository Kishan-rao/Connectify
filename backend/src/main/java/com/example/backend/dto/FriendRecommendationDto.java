package com.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendRecommendationDto {
    private UUID id;
    private String username;
    private int mutualFriends;
    private int sharedGroups;
    private int score;
}
