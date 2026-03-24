package com.example.backend.service;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostCreateRequest;
import com.example.backend.dto.PostResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public PostResponse createPost(Principal principal, PostCreateRequest request) {
        User user = userService.resolveUser(principal.getName());
        Post post = Post.builder()
                .user(user)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build();
        return toResponse(postRepository.save(post));
    }

    public void deletePost(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found."));
        if (!post.getUser().getId().equals(user.getId()))
            throw new AccessDeniedException("You can only delete your own posts.");
        postRepository.delete(post);
    }

    public PagedResponse<PostResponse> getFeed(Principal principal, int page, int size) {
        User user = userService.resolveUser(principal.getName());
        List<User> friends = friendshipRepository.findAllAcceptedFriendships(user).stream()
                .map(f -> f.getRequester().getId().equals(user.getId()) ? f.getAddressee() : f.getRequester())
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> feedPage = postRepository.findFeed(friends, user, pageable);
        return toPagedResponse(feedPage);
    }

    public PagedResponse<PostResponse> getUserPosts(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return toPagedResponse(postsPage);
    }

    private PagedResponse<PostResponse> toPagedResponse(Page<Post> page) {
        List<PostResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    private PostResponse toResponse(Post post) {
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .author(new UserSummaryDto(post.getUser().getId(), post.getUser().getUsername()))
                .build();
    }
}
