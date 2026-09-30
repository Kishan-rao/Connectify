package com.example.backend.dto;

import com.example.backend.entity.GroupType;
import com.example.backend.entity.InvitationStatus;
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
public class GroupInvitationResponse {
    private UUID id;
    private UUID groupId;
    private String groupName;
    private GroupType groupType;
    private UserSummaryDto inviter;
    private InvitationStatus status;
    private LocalDateTime createdAt;
}
