package com.example.backend;

import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.service.PostService;
import com.example.backend.service.UserService;
import com.example.backend.security.JwtAuthenticationFilter;
import com.example.backend.security.JwtService;
import com.example.backend.security.CustomUserDetailsService;
import com.example.backend.controller.UserController;
import com.example.backend.controller.PostController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that GlobalExceptionHandler maps exception types to the correct HTTP status codes.
 */
@WebMvcTest({UserController.class, PostController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(com.example.backend.exception.GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean UserService userService;
    @MockitoBean PostService postService;
    @MockitoBean com.example.backend.service.PostLikeService postLikeService;
    @MockitoBean com.example.backend.service.CommentService commentService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean JwtService jwtService;
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    @Test
    void resourceNotFound_returns404() throws Exception {
        when(userService.getUserProfile(anyString()))
                .thenThrow(new ResourceNotFoundException("User not found: ghost"));

        mockMvc.perform(get("/api/users/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found: ghost"));
    }

    @Test
    void accessDenied_returns403() throws Exception {
        when(postService.createPost(any(), any()))
                .thenThrow(new AccessDeniedException("Forbidden"));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void illegalArgument_returns409() throws Exception {
        when(userService.getUserProfile(anyString()))
                .thenThrow(new IllegalArgumentException("Conflict scenario"));

        mockMvc.perform(get("/api/users/someuser"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void validationError_returns400() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
