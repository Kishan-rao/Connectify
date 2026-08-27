package com.example.backend.service;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PostCreateRequest;
import com.example.backend.dto.PostResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final FriendshipRepository friendshipRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    @SuppressWarnings("null")
    public PostResponse createPost(Principal principal, PostCreateRequest request) {
        User user = userService.resolveUser(principal.getName());
        Post post = Post.builder()
                .user(user)
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .build();
        Post savedPost = Objects.requireNonNull(postRepository.save(post), "Saved post must not be null");
        return toResponse(savedPost, null, null, null);
    }

    public void deletePost(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));
        if (!post.getUser().getId().equals(user.getId()))
            throw new AccessDeniedException("You can only delete your own posts.");
        postRepository.delete(post);
    }

    /**
     * Builds a paginated feed for the current user including:
     *   1. The user's own posts
     *   2. Posts from accepted friends
     *   3. Posts from users who share a group with the current user
     *
     * Relationship data (friend IDs, group-member IDs, group name map) is fetched
     * in bulk BEFORE iterating over posts, so there are NO per-post DB queries.
     *
     * Total DB calls per feed request:
     *   1. findFriendIds          — one bulk query
     *   2. findGroupMemberUserIds — one bulk query
     *   3. findSharedGroupNamesByUserIds — one bulk query (only if group members exist)
     *   4. findFeedByAuthorIds    — one paginated feed query
     */
    public PagedResponse<PostResponse> getFeed(Principal principal, int page, int size) {
        User currentUser = userService.resolveUser(principal.getName());
        UUID currentUserId = currentUser.getId();

        // ── 1. Bulk-fetch friend IDs ──────────────────────────────────────────
        Set<UUID> friendIds = new HashSet<>(friendshipRepository.findFriendIds(currentUserId));

        // ── 2. Bulk-fetch group-member user IDs ──────────────────────────────
        Set<UUID> groupMemberIds = new HashSet<>(groupMembershipRepository.findGroupMemberUserIds(currentUserId));
        // Remove friends and self from groupMemberIds (they already have a stronger relationship)
        groupMemberIds.remove(currentUserId);
        groupMemberIds.removeAll(friendIds);

        // ── 3. Bulk-fetch group name for each group-member author ─────────────
        // Map: authorId → name of first shared group (for explanation text)
        Map<UUID, String> groupMemberGroupName;
        if (!groupMemberIds.isEmpty()) {
            List<Object[]> rows = groupMembershipRepository.findSharedGroupNamesByUserIds(
                    currentUserId, new ArrayList<>(groupMemberIds));
            groupMemberGroupName = rows.stream()
                    .collect(Collectors.toMap(
                            r -> (UUID) r[0],
                            r -> (String) r[1]));
        } else {
            groupMemberGroupName = Collections.emptyMap();
        }

        // ── 4. Build the combined author ID list and fetch paginated posts ────
        Set<UUID> allAuthorIds = new LinkedHashSet<>();
        allAuthorIds.add(currentUserId);
        allAuthorIds.addAll(friendIds);
        allAuthorIds.addAll(groupMemberIds);

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> feedPage = postRepository.findFeedByAuthorIds(new ArrayList<>(allAuthorIds), pageable);

        // ── 5. Map to response using pre-loaded relationship data (no DB calls) ─
        List<PostResponse> content = feedPage.getContent().stream()
                .map(post -> toResponse(post, currentUserId, friendIds, groupMemberGroupName))
                .collect(Collectors.toList());

        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(feedPage.getNumber())
                .size(feedPage.getSize())
                .totalElements(feedPage.getTotalElements())
                .totalPages(feedPage.getTotalPages())
                .build();
    }

    public PagedResponse<PostResponse> getUserPosts(String username, int page, int size) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        // No current-user context here, so no explanation available
        List<PostResponse> content = postsPage.getContent().stream()
                .map(post -> toResponse(post, null, null, null))
                .collect(Collectors.toList());
        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(postsPage.getNumber())
                .size(postsPage.getSize())
                .totalElements(postsPage.getTotalElements())
                .totalPages(postsPage.getTotalPages())
                .build();
    }

    // -------------------------------------------------------------------------
    // Feed Explanation Logic — uses pre-loaded Sets/Maps, zero DB calls
    // -------------------------------------------------------------------------

    /**
     * Resolves "Why am I seeing this?" explanation for a post using
     * pre-loaded relationship data rather than per-post DB queries.
     *
     * Priority: own post > direct friend > shared group member > network fallback.
     *
     * @param post               the feed post
     * @param currentUserId      UUID of the authenticated user (null if no context)
     * @param friendIds          Set of accepted friend UUIDs (pre-loaded)
     * @param groupMemberGroupName Map of groupMember userId → shared group name (pre-loaded)
     */
    private String resolveExplanation(Post post,
                                      UUID currentUserId,
                                      Set<UUID> friendIds,
                                      Map<UUID, String> groupMemberGroupName) {
        if (currentUserId == null) return null;

        UUID authorId = post.getUser().getId();
        String authorUsername = post.getUser().getUsername();

        // 1. Own post
        if (authorId.equals(currentUserId)) {
            return "This is your own post.";
        }

        // 2. Direct friend (pre-loaded, no DB call)
        if (friendIds != null && friendIds.contains(authorId)) {
            return "Posted by your friend @" + authorUsername + ".";
        }

        // 3. Shared group member (pre-loaded, no DB call)
        if (groupMemberGroupName != null) {
            String groupName = groupMemberGroupName.get(authorId);
            if (groupName != null) {
                return "You both are members of the " + groupName + " group.";
            }
        }

        // 4. Network fallback (should only appear if post genuinely entered the feed
        //    through one of the above categories — this is a safety net only)
        return "You may know @" + authorUsername + " through your network.";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PostResponse toResponse(Post post,
                                    UUID currentUserId,
                                    Set<UUID> friendIds,
                                    Map<UUID, String> groupMemberGroupName) {
        String explanation = resolveExplanation(post, currentUserId, friendIds, groupMemberGroupName);
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .author(new UserSummaryDto(post.getUser().getId(), post.getUser().getUsername()))
                .feedExplanation(explanation)
                .build();
    }
}

