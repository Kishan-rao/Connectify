package com.example.backend.service;

import com.example.backend.dto.*;
import com.example.backend.entity.Group;
import com.example.backend.entity.GroupInvitation;
import com.example.backend.entity.GroupMembership;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.InvitationStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.CommentRepository;
import com.example.backend.repository.GroupInvitationRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.GroupRepository;
import com.example.backend.repository.PostLikeRepository;
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
@SuppressWarnings("null")
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final NotificationService notificationService;

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

        boolean isMember = user != null && groupMembershipRepository.existsByGroupAndUser(group, user);

        if (isPrivate(group) && !isMember) {
            return toRestrictedResponse(group);
        }

        long memberCount = groupMembershipRepository.countByGroup(group);
        return toResponse(group, user, memberCount, isMember);
    }

    @Transactional(readOnly = true)
    public PagedResponse<GroupResponse> listGroups(Principal principal, int page, int size) {
        User user = principal != null ? userService.resolveUser(principal.getName()) : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Group> groupsPage = groupRepository.findByOrderByCreatedAtDesc(pageable);

        Set<UUID> userGroupSet = Collections.emptySet();
        if (user != null) {
            userGroupSet = new HashSet<>(groupMembershipRepository.findGroupIdsByUserId(user.getId()));
        }

        final Set<UUID> finalUserGroupSet = userGroupSet;
        final User finalUser = user;

        List<GroupResponse> content = groupsPage.getContent().stream()
                .map(g -> {
                    boolean isMember = finalUserGroupSet.contains(g.getId());
                    if (isPrivate(g) && !isMember) {
                        return toRestrictedResponse(g);
                    }
                    long count = groupMembershipRepository.countByGroup(g);
                    return toResponse(g, finalUser, count, isMember);
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

        if (isPrivate(group)) {
            throw new AccessDeniedException("Private groups require an invitation to join.");
        }

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

    // ── Invitation methods ────────────────────────────────────────────────────

    public GroupInvitationResponse inviteUser(Principal principal, UUID groupId, GroupInviteRequest request) {
        User inviter = userService.resolveUser(principal.getName());
        UUID requiredGroupId = Objects.requireNonNull(groupId, "groupId must not be null");
        Group group = groupRepository.findById(requiredGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + requiredGroupId));

        if (!groupMembershipRepository.existsByGroupAndUser(group, inviter)) {
            throw new AccessDeniedException("You must be a member of the group to invite others.");
        }

        String inviteeUsername = Objects.requireNonNull(request.getUsername(), "username must not be null").trim();
        User invitee = userRepository.findByUsername(inviteeUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + inviteeUsername));

        if (invitee.getId().equals(inviter.getId())) {
            throw new IllegalArgumentException("You cannot invite yourself.");
        }

        if (groupMembershipRepository.existsByGroupAndUser(group, invitee)) {
            throw new IllegalArgumentException("User is already a member of this group.");
        }

        if (groupInvitationRepository.existsByGroupAndInviteeAndStatus(group, invitee, InvitationStatus.PENDING)) {
            throw new IllegalArgumentException("A pending invitation already exists for this user.");
        }

        GroupInvitation invitation = GroupInvitation.builder()
                .group(group)
                .inviter(inviter)
                .invitee(invitee)
                .status(InvitationStatus.PENDING)
                .build();
        GroupInvitation saved = groupInvitationRepository.save(invitation);

        notificationService.createNotification(
                invitee, inviter, NotificationType.GROUP_INVITE, null, null,
                "@" + inviter.getUsername() + " invited you to join " + group.getName() + ".");

        return toInvitationResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GroupInvitationResponse> getMyPendingInvitations(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return groupInvitationRepository.findByInviteeAndStatusOrderByCreatedAtDesc(user, InvitationStatus.PENDING)
                .stream()
                .map(this::toInvitationResponse)
                .collect(Collectors.toList());
    }

    public void acceptInvitation(Principal principal, UUID invitationId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredId = Objects.requireNonNull(invitationId, "invitationId must not be null");
        GroupInvitation invitation = groupInvitationRepository.findById(requiredId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + requiredId));

        if (!invitation.getInvitee().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only accept your own invitations.");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer pending.");
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        groupInvitationRepository.save(invitation);

        // Idempotent: only create membership if not already a member
        if (!groupMembershipRepository.existsByGroupAndUser(invitation.getGroup(), user)) {
            GroupMembership membership = GroupMembership.builder()
                    .group(invitation.getGroup())
                    .user(user)
                    .build();
            groupMembershipRepository.save(membership);
        }
    }

    public void declineInvitation(Principal principal, UUID invitationId) {
        User user = userService.resolveUser(principal.getName());
        UUID requiredId = Objects.requireNonNull(invitationId, "invitationId must not be null");
        GroupInvitation invitation = groupInvitationRepository.findById(requiredId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + requiredId));

        if (!invitation.getInvitee().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only decline your own invitations.");
        }

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Invitation is no longer pending.");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        groupInvitationRepository.save(invitation);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPrivate(Group group) {
        return group.getType() == GroupType.PRIVATE || group.getType() == GroupType.CLOSED;
    }

    /**
     * Returns a minimal GroupResponse for non-members viewing a PRIVATE/CLOSED group.
     * Exposes only id, name, and type; description and other metadata are null.
     */
    private GroupResponse toRestrictedResponse(Group g) {
        return GroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .type(g.getType())
                .description(null)
                .createdBy(null)
                .memberCount(null)
                .isMember(false)
                .createdAt(null)
                .build();
    }

    private GroupResponse toResponse(Group g, User currentUser, Long memberCount, boolean isMember) {
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

    private GroupInvitationResponse toInvitationResponse(GroupInvitation inv) {
        return GroupInvitationResponse.builder()
                .id(inv.getId())
                .groupId(inv.getGroup().getId())
                .groupName(inv.getGroup().getName())
                .groupType(inv.getGroup().getType())
                .inviter(new UserSummaryDto(inv.getInviter().getId(), inv.getInviter().getUsername()))
                .status(inv.getStatus())
                .createdAt(inv.getCreatedAt())
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
}
