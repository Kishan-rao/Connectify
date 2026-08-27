package com.example.backend.service;

import com.example.backend.dto.PagedResponse;
import com.example.backend.dto.PublicUserProfileResponse;
import com.example.backend.dto.UserProfileResponse;
import com.example.backend.dto.UserSearchResultDto;
import com.example.backend.entity.User;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.PostRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostRepository postRepository;

    /** Returns the authenticated user's own profile including email. */
    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Principal principal) {
        User user = resolveUser(principal.getName());
        return buildPrivateProfile(user);
    }

    /** Returns a public profile view that does NOT include email. */
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return buildPublicProfile(user);
    }

    /**
     * Case-insensitive paginated user search.
     * Safely omits emails and enriches with current relationship status.
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserSearchResultDto> searchUsers(Principal principal, String query, int page, int size) {
        if (query == null || query.trim().isEmpty()) {
            return PagedResponse.<UserSearchResultDto>builder()
                    .content(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .build();
        }

        User currentUser = principal != null ? resolveUser(principal.getName()) : null;
        UUID currentUserId = currentUser != null ? currentUser.getId() : null;

        Pageable pageable = PageRequest.of(page, size);
        Page<User> usersPage = userRepository.findByUsernameContainingIgnoreCase(query.trim(), pageable);

        // Pre-load relationship data for current user
        Set<UUID> friendIds = currentUserId != null
                ? new HashSet<>(friendshipRepository.findFriendIds(currentUserId))
                : Collections.emptySet();
        Set<UUID> pendingSentIds = currentUserId != null
                ? new HashSet<>(friendshipRepository.findPendingSentAddresseeIds(currentUserId))
                : Collections.emptySet();
        Set<UUID> pendingRecvIds = currentUserId != null
                ? new HashSet<>(friendshipRepository.findPendingReceivedRequesterIds(currentUserId))
                : Collections.emptySet();

        List<UserSearchResultDto> content = usersPage.getContent().stream()
                .map(u -> {
                    String status;
                    if (currentUserId != null && u.getId().equals(currentUserId)) {
                        status = "SELF";
                    } else if (friendIds.contains(u.getId())) {
                        status = "FRIENDS";
                    } else if (pendingSentIds.contains(u.getId())) {
                        status = "PENDING_SENT";
                    } else if (pendingRecvIds.contains(u.getId())) {
                        status = "PENDING_RECEIVED";
                    } else {
                        status = "NONE";
                    }

                    long friendCount = friendshipRepository.findAllAcceptedFriendships(u).size();

                    return UserSearchResultDto.builder()
                            .id(u.getId())
                            .username(u.getUsername())
                            .createdAt(u.getCreatedAt())
                            .friendCount(friendCount)
                            .relationshipStatus(status)
                            .build();
                })
                .collect(Collectors.toList());

        return PagedResponse.<UserSearchResultDto>builder()
                .content(content)
                .page(usersPage.getNumber())
                .size(usersPage.getSize())
                .totalElements(usersPage.getTotalElements())
                .totalPages(usersPage.getTotalPages())
                .build();
    }

    private UserProfileResponse buildPrivateProfile(User user) {
        long friendCount = friendshipRepository.findAllAcceptedFriendships(user).size();
        long postCount = postRepository.countByUser(user);
        return UserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .friendCount(friendCount)
                .postCount(postCount)
                .build();
    }

    private PublicUserProfileResponse buildPublicProfile(User user) {
        long friendCount = friendshipRepository.findAllAcceptedFriendships(user).size();
        long postCount = postRepository.countByUser(user);
        return PublicUserProfileResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .createdAt(user.getCreatedAt())
                .friendCount(friendCount)
                .postCount(postCount)
                .build();
    }

    /**
     * Resolves the authenticated principal's username/email to a User entity.
     * Throws {@link UsernameNotFoundException} (Spring Security contract) rather
     * than {@link ResourceNotFoundException} so that the JWT filter and
     * authentication machinery continue to work correctly.
     */
    @Transactional(readOnly = true)
    public User resolveUser(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
    }
}


