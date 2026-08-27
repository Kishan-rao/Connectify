package com.example.backend.controller;

import com.example.backend.dto.PublicUserProfileResponse;
import com.example.backend.dto.UserProfileResponse;
import com.example.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Private — returns full profile including email for the authenticated user. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Principal principal) {
        return ResponseEntity.ok(userService.getMyProfile(principal));
    }

    /** Search users by username query with pagination. */
    @GetMapping("/search")
    public ResponseEntity<com.example.backend.dto.PagedResponse<com.example.backend.dto.UserSearchResultDto>> searchUsers(
            Principal principal,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(userService.searchUsers(principal, q, page, size));
    }

    /** Public — returns profile WITHOUT email when viewing another user's profile. */
    @GetMapping("/{username}")
    public ResponseEntity<PublicUserProfileResponse> getUserProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserProfile(username));
    }
}

