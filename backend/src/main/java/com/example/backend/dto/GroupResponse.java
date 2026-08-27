package com.example.backend.dto;

import com.example.backend.entity.GroupType;
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
public class GroupResponse {
    private UUID id;
    private String name;
    private String description;
    private GroupType type;
    private UserSummaryDto createdBy;
    private long memberCount;
    private boolean isMember;
    private LocalDateTime createdAt;
}
