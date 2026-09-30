package com.example.backend.service;

import com.example.backend.dto.FriendRecommendationDto;
import com.example.backend.dto.FriendRequestDto;
import com.example.backend.dto.FriendshipResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Friendship;
import com.example.backend.entity.FriendshipStatus;
import com.example.backend.entity.NotificationType;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@SuppressWarnings("null")
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    @SuppressWarnings("null")
    public FriendshipResponse sendRequest(Principal principal, FriendRequestDto dto) {
        User requester = userService.resolveUser(principal.getName());
        User addressee = userRepository.findByUsername(dto.getAddresseeUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + dto.getAddresseeUsername()));

        if (requester.getId().equals(addressee.getId()))
            throw new IllegalArgumentException("You cannot send a friend request to yourself.");

        Optional<Friendship> existing = friendshipRepository.findBetween(requester, addressee);
        Friendship friendship;
        if (existing.isPresent()) {
            Friendship current = existing.get();
            if (current.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new IllegalArgumentException("You are already friends with this user.");
            }
            if (current.getStatus() == FriendshipStatus.PENDING) {
                throw new IllegalArgumentException("A friend request already exists between these users.");
            }
            // If previously REJECTED, reset to PENDING with new requester/addressee
            current.setRequester(requester);
            current.setAddressee(addressee);
            current.setStatus(FriendshipStatus.PENDING);
            friendship = current;
        } else {
            friendship = Friendship.builder()
                    .requester(requester)
                    .addressee(addressee)
                    .status(FriendshipStatus.PENDING)
                    .build();
        }

        Friendship savedFriendship = Objects.requireNonNull(
                friendshipRepository.save(friendship),
                "Saved friendship must not be null");

        // Notify addressee of new friend request
        notificationService.createNotification(
                addressee,
                requester,
                NotificationType.FRIEND_REQUEST,
                null,
                savedFriendship,
                null
        );

        return toResponse(savedFriendship);
    }

    public FriendshipResponse respondToRequest(Principal principal, UUID friendshipId, boolean accept) {
        User currentUser = userService.resolveUser(principal.getName());
        UUID requiredFriendshipId = Objects.requireNonNull(friendshipId, "friendshipId must not be null");
        Friendship friendship = friendshipRepository.findById(requiredFriendshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Friendship not found: " + requiredFriendshipId));

        if (!friendship.getAddressee().getId().equals(currentUser.getId()))
            throw new AccessDeniedException("You are not the recipient of this request.");

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException("Cannot respond to a friendship request that is not pending.");
        }

        friendship.setStatus(accept ? FriendshipStatus.ACCEPTED : FriendshipStatus.REJECTED);
        Friendship savedFriendship = Objects.requireNonNull(
                friendshipRepository.save(friendship),
                "Saved friendship must not be null");

        if (accept) {
            // Notify original requester that request was accepted
            notificationService.createNotification(
                    friendship.getRequester(),
                    currentUser,
                    NotificationType.FRIEND_REQUEST_ACCEPTED,
                    null,
                    savedFriendship,
                    null
            );
        }

        return toResponse(savedFriendship);
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listFriends(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return friendshipRepository.findAllAcceptedFriendships(user).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(user.getId()) ? f.getAddressee() : f.getRequester();
                    return new UserSummaryDto(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUserFriends(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return friendshipRepository.findAllAcceptedFriendships(user).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(user.getId()) ? f.getAddressee() : f.getRequester();
                    return new UserSummaryDto(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> listPendingReceived(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return friendshipRepository.findPendingRequests(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Graph-based Friend Recommendation Algorithm:
     *   Score = (+5 * mutualFriends) + (+3 * sharedGroups)
     *
     * Strict filtering:
     *   - Excludes self
     *   - Excludes current accepted friends
     *   - Excludes pending sent requests
     *   - Excludes pending received requests
     *
     * Bulk processing:
     *   - Zero N+1 queries
     *   - Candidates discovered from friends-of-friends and shared group members
     *   - Ordered by score descending
     */
    @Transactional(readOnly = true)
    public List<FriendRecommendationDto> getSuggestedFriends(Principal principal) {
        User currentUser = userService.resolveUser(principal.getName());
        UUID currentUserId = currentUser.getId();

        // 1. Bulk-load exclusion sets
        Set<UUID> myFriendIds = new HashSet<>(friendshipRepository.findFriendIds(currentUserId));
        Set<UUID> pendingSentIds = new HashSet<>(friendshipRepository.findPendingSentAddresseeIds(currentUserId));
        Set<UUID> pendingRecvIds = new HashSet<>(friendshipRepository.findPendingReceivedRequesterIds(currentUserId));

        Set<UUID> excludedIds = new HashSet<>(myFriendIds);
        excludedIds.add(currentUserId);
        excludedIds.addAll(pendingSentIds);
        excludedIds.addAll(pendingRecvIds);

        // 2. Bulk-load current user's groups
        Set<UUID> myGroupIds = new HashSet<>(groupMembershipRepository.findGroupIdsByUserId(currentUserId));

        // 3. Candidate discovery & metric calculation in memory
        Map<UUID, Integer> mutualFriendCountMap = new HashMap<>();
        Map<UUID, Integer> sharedGroupCountMap = new HashMap<>();

        // 3a. Discover candidates from friends-of-friends
        for (UUID friendId : myFriendIds) {
            List<UUID> friendsOfFriend = friendshipRepository.findFriendIds(friendId);
            for (UUID candidateId : friendsOfFriend) {
                if (!excludedIds.contains(candidateId)) {
                    mutualFriendCountMap.put(candidateId, mutualFriendCountMap.getOrDefault(candidateId, 0) + 1);
                }
            }
        }

        // 3b. Discover candidates from shared groups
        if (!myGroupIds.isEmpty()) {
            List<UUID> groupMemberIds = groupMembershipRepository.findGroupMemberUserIds(currentUserId);
            for (UUID candidateId : groupMemberIds) {
                if (!excludedIds.contains(candidateId)) {
                    // Count how many groups candidate shares with currentUser
                    List<UUID> candidateGroupIds = groupMembershipRepository.findGroupIdsByUserId(candidateId);
                    int sharedCount = 0;
                    for (UUID gid : candidateGroupIds) {
                        if (myGroupIds.contains(gid)) sharedCount++;
                    }
                    if (sharedCount > 0) {
                        sharedGroupCountMap.put(candidateId, sharedCount);
                    }
                }
            }
        }

        // 4. Combine all candidate IDs
        Set<UUID> allCandidateIds = new HashSet<>();
        allCandidateIds.addAll(mutualFriendCountMap.keySet());
        allCandidateIds.addAll(sharedGroupCountMap.keySet());

        if (allCandidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. Bulk fetch candidate user entities
        List<User> candidateUsers = userRepository.findAllById(allCandidateIds);
        Map<UUID, User> candidateUserMap = candidateUsers.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 6. Score and rank candidates
        List<FriendRecommendationDto> recommendations = new ArrayList<>();
        for (UUID candidateId : allCandidateIds) {
            User candidate = candidateUserMap.get(candidateId);
            if (candidate == null) continue;

            int mutualFriends = mutualFriendCountMap.getOrDefault(candidateId, 0);
            int sharedGroups = sharedGroupCountMap.getOrDefault(candidateId, 0);
            int score = (mutualFriends * 5) + (sharedGroups * 3);

            if (score > 0) {
                recommendations.add(FriendRecommendationDto.builder()
                        .id(candidate.getId())
                        .username(candidate.getUsername())
                        .mutualFriends(mutualFriends)
                        .sharedGroups(sharedGroups)
                        .score(score)
                        .build());
            }
        }

        // Sort descending by score, then ascending by username
        recommendations.sort(Comparator.comparingInt(FriendRecommendationDto::getScore).reversed()
                .thenComparing(FriendRecommendationDto::getUsername));

        return recommendations;
    }

    private FriendshipResponse toResponse(Friendship f) {
        return FriendshipResponse.builder()
                .id(f.getId())
                .status(f.getStatus())
                .requester(new UserSummaryDto(f.getRequester().getId(), f.getRequester().getUsername()))
                .addressee(new UserSummaryDto(f.getAddressee().getId(), f.getAddressee().getUsername()))
                .createdAt(f.getCreatedAt())
                .build();
    }
}

