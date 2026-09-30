package com.example.backend.controller;

import com.example.backend.dto.*;
import com.example.backend.service.CommentService;
import com.example.backend.service.PostLikeService;
import com.example.backend.service.PostService;
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
@RequiredArgsConstructor
@Validated
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;
    private final CommentService commentService;

    @PostMapping("/api/posts")
    public ResponseEntity<PostResponse> createPost(
            Principal principal,
            @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(principal, request));
    }

    @DeleteMapping("/api/posts/{id}")
    public ResponseEntity<Void> deletePost(Principal principal, @PathVariable UUID id) {
        postService.deletePost(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/feed")
    public ResponseEntity<PagedResponse<PostResponse>> getFeed(
            Principal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(postService.getFeed(principal, page, size));
    }

    @GetMapping("/api/posts/user/{username}")
    public ResponseEntity<PagedResponse<PostResponse>> getUserPosts(
            Principal principal,
            @PathVariable String username,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(postService.getUserPosts(principal, username, page, size));
    }

    // ── Likes Endpoints ───────────────────────────────────────────────────────

    @PostMapping("/api/posts/{id}/like")
    public ResponseEntity<Void> likePost(Principal principal, @PathVariable UUID id) {
        postLikeService.likePost(principal, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/api/posts/{id}/like")
    public ResponseEntity<Void> unlikePost(Principal principal, @PathVariable UUID id) {
        postLikeService.unlikePost(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/posts/{id}/likes")
    public ResponseEntity<List<UserSummaryDto>> getLikes(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(postLikeService.getLikes(principal, id));
    }

    // ── Comments Endpoints ────────────────────────────────────────────────────

    @GetMapping("/api/posts/{id}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(Principal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(commentService.getComments(principal, id));
    }

    @PostMapping("/api/posts/{id}/comments")
    public ResponseEntity<CommentResponse> createComment(
            Principal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.createComment(principal, id, request));
    }
}
