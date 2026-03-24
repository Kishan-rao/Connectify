package com.example.backend.controller;

import com.example.backend.dto.FriendRequestDto;
import com.example.backend.dto.FriendshipResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.service.FriendshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping
    public ResponseEntity<FriendshipResponse> sendRequest(
            Principal principal,
            @Valid @RequestBody FriendRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendshipService.sendRequest(principal, dto));
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<FriendshipResponse> acceptRequest(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(friendshipService.respondToRequest(principal, id, true));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<FriendshipResponse> rejectRequest(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(friendshipService.respondToRequest(principal, id, false));
    }

    @GetMapping("/friends")
    public ResponseEntity<List<UserSummaryDto>> listFriends(Principal principal) {
        return ResponseEntity.ok(friendshipService.listFriends(principal));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FriendshipResponse>> listPending(Principal principal) {
        return ResponseEntity.ok(friendshipService.listPendingReceived(principal));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<UserSummaryDto>> getSuggestions(Principal principal) {
        return ResponseEntity.ok(friendshipService.getSuggestedFriends(principal));
    }
}
