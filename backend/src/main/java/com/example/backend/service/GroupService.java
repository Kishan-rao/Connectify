package com.example.backend.service;

import com.example.backend.dto.*;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupMembership;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CommentRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.GroupRepository;
import com.example.backend.repository.PostLikeRepository;
import com.example.backend.repository.PostRepository;
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
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;

    public GroupResponse createGroup(Principal principal, GroupCreateRequest request) {
        User user = userService.resolveUser(principal.getName());

        String name = request.getName().trim();
        if (groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("A group with this name already exists: " + name);
        }

        Group group = Group.builder()
                .name(name)
                .description(request.getDescription())
                .type(request.getType())
                .createdBy(user)
                .build();
        Group savedGroup = groupRepository.save(group);

        // Creator automatically joins the group
        GroupMembership membership = GroupMembership.builder()
                .group(savedGroup)
                .user(user)
                .build();
        groupMembershipRepository.save(membership);

        return toResponse(savedGroup, user, 1L, true);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(Principal principal, UUID groupId) {
        User user = principal != null ? userService.resolveUser(principal.getName()) : null;
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        long memberCount = groupMembershipRepository.countByGroup(group);
        boolean isMember = user != null && groupMembershipRepository.existsByGroupAndUser(group, user);

        return toResponse(group, user, memberCount, isMember);
    }

    @Transactional(readOnly = true)
    public PagedResponse<GroupResponse> listGroups(Principal principal, int page, int size) {
        User user = userService.resolveUser(principal.getName());
        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groupsPage = groupRepository.findByOrderByCreatedAtDesc(pageable);

        List<UUID> userGroupIds = groupMembershipRepository.findGroupIdsByUserId(user.getId());
        Set<UUID> userGroupSet = new HashSet<>(userGroupIds);

        List<GroupResponse> content = groupsPage.getContent().stream()
                .map(g -> {
                    long count = groupMembershipRepository.countByGroup(g);
                    boolean isMember = userGroupSet.contains(g.getId());
                    return toResponse(g, user, count, isMember);
                })
                .collect(Collectors.toList());

        return PagedResponse.<GroupResponse>builder()
                .content(content)
                .page(groupsPage.getNumber())
                .size(groupsPage.getSize())
                .totalElements(groupsPage.getTotalElements())
                .totalPages(groupsPage.getTotalPages())
                .build();
    }

    public void joinGroup(Principal principal, UUID groupId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        if (groupMembershipRepository.existsByGroupAndUser(group, user)) {
            throw new IllegalArgumentException("You are already a member of this group.");
        }

        GroupMembership membership = GroupMembership.builder()
                .group(group)
                .user(user)
                .build();
        groupMembershipRepository.save(membership);
    }

    public void leaveGroup(Principal principal, UUID groupId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        GroupMembership membership = groupMembershipRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this group."));

        groupMembershipRepository.delete(membership);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> getMembers(Principal principal, UUID groupId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        if (isPrivate(group) && !groupMembershipRepository.existsByGroupAndUser(group, user)) {
            throw new AccessDeniedException("Private group members can only be viewed by group members.");
        }

        return groupMembershipRepository.findByGroupOrderByJoinedAtAsc(group).stream()
                .map(m -> new UserSummaryDto(m.getUser().getId(), m.getUser().getUsername()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getGroupPosts(Principal principal, UUID groupId, int page, int size) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        if (isPrivate(group) && !groupMembershipRepository.existsByGroupAndUser(group, user)) {
            throw new AccessDeniedException("Private group posts can only be viewed by group members.");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> postsPage = postRepository.findByGroupOrderByCreatedAtDesc(group, pageable);

        List<UUID> postIds = postsPage.getContent().stream().map(Post::getId).collect(Collectors.toList());

        // Bulk load likes and comments
        Map<UUID, Long> likeCounts = bulkFetchLikeCounts(postIds);
        Map<UUID, Long> commentCounts = bulkFetchCommentCounts(postIds);
        Set<UUID> userLikedPostIds = bulkFetchUserLikes(user.getId(), postIds);

        List<PostResponse> content = postsPage.getContent().stream()
                .map(p -> toPostResponse(p, user.getId(), likeCounts, commentCounts, userLikedPostIds))
                .collect(Collectors.toList());

        return PagedResponse.<PostResponse>builder()
                .content(content)
                .page(postsPage.getNumber())
                .size(postsPage.getSize())
                .totalElements(postsPage.getTotalElements())
                .totalPages(postsPage.getTotalPages())
                .build();
    }

    private boolean isPrivate(Group group) {
        return group.getType() == GroupType.PRIVATE || group.getType() == GroupType.CLOSED;
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
        if (postIds.isEmpty()) return Collections.emptySet();
        return new HashSet<>(postLikeRepository.findLikedPostIdsByUserIdAndPostIds(userId, postIds));
    }

    private GroupResponse toResponse(Group g, User currentUser, long memberCount, boolean isMember) {
        UserSummaryDto creatorDto = g.getCreatedBy() != null
                ? new UserSummaryDto(g.getCreatedBy().getId(), g.getCreatedBy().getUsername())
                : null;
        return GroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .type(g.getType())
                .createdBy(creatorDto)
                .memberCount(memberCount)
                .isMember(isMember)
                .createdAt(g.getCreatedAt())
                .build();
    }

    private PostResponse toPostResponse(Post post, UUID currentUserId,
                                        Map<UUID, Long> likeCounts,
                                        Map<UUID, Long> commentCounts,
                                        Set<UUID> userLikedPostIds) {
        GroupSummaryDto groupDto = post.getGroup() != null
                ? new GroupSummaryDto(post.getGroup().getId(), post.getGroup().getName(), post.getGroup().getType())
                : null;
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .author(new UserSummaryDto(post.getUser().getId(), post.getUser().getUsername()))
                .feedExplanation("Posted in " + post.getGroup().getName() + " group.")
                .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                .likedByCurrentUser(userLikedPostIds.contains(post.getId()))
                .group(groupDto)
                .build();
    }
}
