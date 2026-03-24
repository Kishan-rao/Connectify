package com.example.backend.controller;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostCreateRequest;
import com.example.backend.dto.PostResponse;
import com.example.backend.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getFeed(principal, page, size));
    }

    @GetMapping("/api/posts/user/{username}")
    public ResponseEntity<PagedResponse<PostResponse>> getUserPosts(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postService.getUserPosts(username, page, size));
    }
}
