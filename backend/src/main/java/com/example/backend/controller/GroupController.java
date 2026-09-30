package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public ResponseEntity<PagedResponse<GroupResponse>> listGroups(
            Principal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
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

    // ── Invitations endpoints (placed before /{id} to avoid path collision) ───

    @GetMapping("/invitations")
    public ResponseEntity<List<GroupInvitationResponse>> getMyPendingInvitations(Principal principal) {
        return ResponseEntity.ok(groupService.getMyPendingInvitations(principal));
    }

    @PostMapping("/invitations/{invId}/accept")
    public ResponseEntity<Void> acceptInvitation(Principal principal, @PathVariable UUID invId) {
        groupService.acceptInvitation(principal, invId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invitations/{invId}/decline")
    public ResponseEntity<Void> declineInvitation(Principal principal, @PathVariable UUID invId) {
        groupService.declineInvitation(principal, invId);
        return ResponseEntity.ok().build();
    }

    // ── Group specific endpoints ─────────────────────────────────────────────

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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok(groupService.getGroupPosts(principal, id, page, size));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<GroupInvitationResponse> inviteUser(
            Principal principal,
            @PathVariable UUID id,
            @Valid @RequestBody GroupInviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupService.inviteUser(principal, id, request));
    }
}
