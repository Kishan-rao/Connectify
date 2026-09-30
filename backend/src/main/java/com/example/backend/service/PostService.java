package com.example.backend.service;

import com.example.backend.dto.*;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.*;
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
@SuppressWarnings("null")
public class PostService {

    private final PostRepository postRepository;
    private final FriendshipRepository friendshipRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final GroupRepository groupRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public PostResponse createPost(Principal principal, PostCreateRequest request) {
        User user = userService.resolveUser(principal.getName());

        Group group = null;
        if (request.getGroupId() != null) {
            UUID groupId = request.getGroupId();
            group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

            if (!groupMembershipRepository.existsByGroupAndUser(group, user)) {
                throw new AccessDeniedException("You must be a member of this group to create a post in it.");
            }
        }

        Post post = Post.builder()
                .user(user)
                .group(group)
                .content(request.getContent().trim())
                .imageUrl(request.getImageUrl())
                .build();
        Post savedPost = Objects.requireNonNull(postRepository.save(post), "Saved post must not be null");

        GroupSummaryDto groupDto = group != null
                ? new GroupSummaryDto(group.getId(), group.getName(), group.getType())
                : null;

        return PostResponse.builder()
                .id(savedPost.getId())
                .content(savedPost.getContent())
                .imageUrl(savedPost.getImageUrl())
                .createdAt(savedPost.getCreatedAt())
                .author(new UserSummaryDto(user.getId(), user.getUsername()))
                .feedExplanation("This is your own post.")
                .likeCount(0L)
                .commentCount(0L)
                .likedByCurrentUser(false)
                .group(groupDto)
                .build();
    }

    public void deletePost(Principal principal, UUID postId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredPostId = Objects.requireNonNull(postId, "postId must not be null");
        Post post = postRepository.findById(requiredPostId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + requiredPostId));
        if (!post.getUser().getId().equals(user.getId()))
            throw new AccessDeniedException("You can only delete your own posts.");

        // Child tables hold foreign keys to posts, so remove them before the post.
        notificationRepository.deleteByPost(post);
        commentRepository.deleteByPost(post);
        postLikeRepository.deleteByPost(post);
        postRepository.delete(post);
    }

    /**
     * Builds a paginated feed for the current user including:
     *   1. The user's own posts
     *   2. Personal posts from accepted friends
     *   3. Posts from groups the user belongs to
     *
     * Total DB calls per feed request:
     *   1. findFriendIds
     *   2. findGroupIdsByUserId
     *   3. findGroupMemberUserIds
     *   4. findSharedGroupNamesByUserIds (if group members exist)
     *   5. findUnifiedFeed / findFeedByAuthorIds (single paginated query)
     *   6. countLikesByPostIds (bulk query for page items)
     *   7. countCommentsByPostIds (bulk query for page items)
     *   8. findLikedPostIdsByUserIdAndPostIds (bulk query for page items)
     *
     * ZERO per-post database queries!
     */
    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getFeed(Principal principal, int page, int size) {
        User currentUser = userService.resolveUser(principal.getName());
        UUID currentUserId = currentUser.getId();

        // 1. Bulk-fetch friend IDs
        Set<UUID> friendIds = new HashSet<>(friendshipRepository.findFriendIds(currentUserId));

        // 2. Bulk-fetch groups the user belongs to
        List<UUID> myGroupIds = groupMembershipRepository.findGroupIdsByUserId(currentUserId);

        // 3. Personal posts are visible only from the current user and direct friends.
        // Shared-group members contribute only posts made inside a group the viewer belongs to.
        Set<UUID> allAuthorIds = new LinkedHashSet<>();
        allAuthorIds.add(currentUserId);
        allAuthorIds.addAll(friendIds);

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> feedPage;
        if (!myGroupIds.isEmpty()) {
            feedPage = postRepository.findUnifiedFeed(new ArrayList<>(allAuthorIds), myGroupIds, pageable);
        } else {
            feedPage = postRepository.findFeedByAuthorIds(new ArrayList<>(allAuthorIds), pageable);
        }

        List<UUID> postIds = feedPage.getContent().stream().map(Post::getId).collect(Collectors.toList());

        // 6. Bulk-fetch likes, comments, and like status
        Map<UUID, Long> likeCounts = bulkFetchLikeCounts(postIds);
        Map<UUID, Long> commentCounts = bulkFetchCommentCounts(postIds);
        Set<UUID> userLikedPostIds = bulkFetchUserLikes(currentUserId, postIds);

        // 7. Map to response in memory
        List<PostResponse> content = feedPage.getContent().stream()
                .map(post -> toResponse(post, currentUserId, friendIds, Collections.emptyMap(),
                        likeCounts, commentCounts, userLikedPostIds))
                .collect(Collectors.toList());

        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(feedPage.getNumber())
                .size(feedPage.getSize())
                .totalElements(feedPage.getTotalElements())
                .totalPages(feedPage.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getUserPosts(Principal principal, String username, int page, int size) {
        User currentUser = userService.resolveUser(principal.getName());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        Pageable pageable = PageRequest.of(page, size);
        List<UUID> visibleGroupIds = groupMembershipRepository.findGroupIdsByUserId(currentUser.getId());
        if (visibleGroupIds.isEmpty()) {
            // Avoid provider-specific behavior for an empty IN clause.
            visibleGroupIds = List.of(new UUID(0L, 0L));
        }
        Page<Post> postsPage = postRepository.findVisibleByUser(
                user, List.of(GroupType.PRIVATE, GroupType.CLOSED), visibleGroupIds, pageable);

        List<UUID> postIds = postsPage.getContent().stream().map(Post::getId).collect(Collectors.toList());
        Map<UUID, Long> likeCounts = bulkFetchLikeCounts(postIds);
        Map<UUID, Long> commentCounts = bulkFetchCommentCounts(postIds);
        Set<UUID> userLikedPostIds = bulkFetchUserLikes(currentUser.getId(), postIds);

        List<PostResponse> content = postsPage.getContent().stream()
                .map(post -> toResponse(post, currentUser.getId(), Collections.emptySet(), Collections.emptyMap(),
                        likeCounts, commentCounts, userLikedPostIds))
                .collect(Collectors.toList());

        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(postsPage.getNumber())
                .size(postsPage.getSize())
                .totalElements(postsPage.getTotalElements())
                .totalPages(postsPage.getTotalPages())
                .build();
    }

    private Map<UUID, Long> bulkFetchLikeCounts(List<UUID> postIds) {
        if (postIds.isEmpty()) return Collections.emptyMap();
        return postLikeRepository.countLikesByPostIds(postIds).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
    }

    private Map<UUID, Long> bulkFetchCommentCounts(List<UUID> postIds) {
        if (postIds.isEmpty()) return Collections.emptyMap();
        return commentRepository.countCommentsByPostIds(postIds).stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
    }

    private Set<UUID> bulkFetchUserLikes(UUID userId, List<UUID> postIds) {
        if (postIds.isEmpty() || userId == null) return Collections.emptySet();
        return new HashSet<>(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(userId, postIds));
    }

    private String resolveExplanation(Post post,
                                      UUID currentUserId,
                                      Set<UUID> friendIds,
                                      Map<UUID, String> groupMemberGroupName,
                                      Set<UUID> userGroupIds) {
        if (currentUserId == null) return null;

        UUID authorId = post.getUser().getId();
        String authorUsername = post.getUser().getUsername();

        // 1. Group post
        if (post.getGroup() != null) {
            return "Posted in " + post.getGroup().getName() + " group.";
        }

        // 2. Own post
        if (authorId.equals(currentUserId)) {
            return "This is your own post.";
        }

        // 3. Direct friend
        if (friendIds != null && friendIds.contains(authorId)) {
            return "Posted by your friend @" + authorUsername + ".";
        }

        // 4. Shared group member
        if (groupMemberGroupName != null) {
            String groupName = groupMemberGroupName.get(authorId);
            if (groupName != null) {
                return "You both belong to " + groupName + ".";
            }
        }

        return "You may know @" + authorUsername + " through your network.";
    }

    private PostResponse toResponse(Post post,
                                    UUID currentUserId,
                                    Set<UUID> friendIds,
                                    Map<UUID, String> groupMemberGroupName,
                                    Map<UUID, Long> likeCounts,
                                    Map<UUID, Long> commentCounts,
                                    Set<UUID> userLikedPostIds) {
        String explanation = resolveExplanation(post, currentUserId, friendIds, groupMemberGroupName, Collections.emptySet());
        GroupSummaryDto groupDto = post.getGroup() != null
                ? new GroupSummaryDto(post.getGroup().getId(), post.getGroup().getName(), post.getGroup().getType())
                : null;

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .author(new UserSummaryDto(post.getUser().getId(), post.getUser().getUsername()))
                .feedExplanation(explanation)
                .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                .likedByCurrentUser(userLikedPostIds.contains(post.getId()))
                .group(groupDto)
                .build();
    }
}
