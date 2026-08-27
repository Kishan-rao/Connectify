package com.example.backend.dto;

import com.example.backend.entity.GroupType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummaryDto {
    private UUID id;
    private String name;
    private GroupType type;
}
