package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<PagedResponse<GroupResponse>> listGroups(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(groupService.listGroups(principal, page, size));
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(
            Principal principal,
            @Valid @RequestBody GroupCreateRequest request
    ) {
        return ResponseEntity.ok(groupService.createGroup(principal, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroup(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(groupService.getGroup(principal, id));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<Void> joinGroup(Principal principal, @PathVariable UUID id) {
        groupService.joinGroup(principal, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<Void> leaveGroup(Principal principal, @PathVariable UUID id) {
        groupService.leaveGroup(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<UserSummaryDto>> getMembers(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(groupService.getMembers(principal, id));
    }

    @GetMapping("/{id}/posts")
    public ResponseEntity<PagedResponse<PostResponse>> getGroupPosts(
            Principal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(groupService.getGroupPosts(principal, id, page, size));
    }
}
