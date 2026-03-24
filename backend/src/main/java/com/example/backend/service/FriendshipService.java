package com.example.backend.service;

import com.example.backend.dto.FriendRequestDto;
import com.example.backend.dto.FriendshipResponse;
import com.example.backend.dto.UserSummaryDto;
import com.example.backend.entity.Friendship;
import com.example.backend.entity.FriendshipStatus;
import com.example.backend.entity.User;
import com.example.backend.repository.FriendshipRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public FriendshipResponse sendRequest(Principal principal, FriendRequestDto dto) {
        User requester = userService.resolveUser(principal.getName());
        User addressee = userRepository.findByUsername(dto.getAddresseeUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + dto.getAddresseeUsername()));

        if (requester.getId().equals(addressee.getId()))
            throw new IllegalArgumentException("You cannot send a friend request to yourself.");
        if (friendshipRepository.existsByRequesterAndAddressee(requester, addressee) ||
                friendshipRepository.existsByRequesterAndAddressee(addressee, requester))
            throw new IllegalArgumentException("A friend request already exists between these users.");

        Friendship friendship = Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendshipStatus.PENDING)
                .build();
        return toResponse(friendshipRepository.save(friendship));
    }

    public FriendshipResponse respondToRequest(Principal principal, UUID friendshipId, boolean accept) {
        User currentUser = userService.resolveUser(principal.getName());
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friendship not found."));

        if (!friendship.getAddressee().getId().equals(currentUser.getId()))
            throw new IllegalArgumentException("You are not the recipient of this request.");

        friendship.setStatus(accept ? FriendshipStatus.ACCEPTED : FriendshipStatus.REJECTED);
        return toResponse(friendshipRepository.save(friendship));
    }

    public List<UserSummaryDto> listFriends(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return friendshipRepository.findAllAcceptedFriendships(user).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(user.getId()) ? f.getAddressee() : f.getRequester();
                    return new UserSummaryDto(friend.getId(), friend.getUsername());
                })
                .collect(Collectors.toList());
    }

    public List<FriendshipResponse> listPendingReceived(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        return friendshipRepository.findPendingRequests(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<UserSummaryDto> getSuggestedFriends(Principal principal) {
        User user = userService.resolveUser(principal.getName());
        List<User> myFriends = friendshipRepository.findAllAcceptedFriendships(user).stream()
                .map(f -> f.getRequester().getId().equals(user.getId()) ? f.getAddressee() : f.getRequester())
                .collect(Collectors.toList());

        return myFriends.stream()
                .flatMap(friend -> friendshipRepository.findAllAcceptedFriendships(friend).stream()
                        .map(f -> f.getRequester().getId().equals(friend.getId()) ? f.getAddressee() : f.getRequester()))
                .distinct()
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .filter(candidate -> myFriends.stream().noneMatch(f -> f.getId().equals(candidate.getId())))
                .map(candidate -> new UserSummaryDto(candidate.getId(), candidate.getUsername()))
                .collect(Collectors.toList());
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
