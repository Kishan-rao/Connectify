package com.example.backend.controller;

import com.example.backend.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(Principal principal, @PathVariable UUID commentId) {
        commentService.deleteComment(principal, commentId);
        return ResponseEntity.noContent().build();
    }
}
