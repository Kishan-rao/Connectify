package com.example.backend;

import com.example.backend.dto.FriendRecommendationDto;
import com.example.backend.dto.GroupCreateRequest;
import com.example.backend.dto.GroupResponse;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.FriendshipService;
import com.example.backend.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FriendRecommendationScoringTest {

    @Autowired FriendshipService friendshipService;
    @Autowired GroupService groupService;
    @Autowired UserRepository userRepository;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, charlie, dave, eve;

    @BeforeEach
    void setUp() {
        alice   = saveUser("alice");
        bob     = saveUser("bob");
        charlie = saveUser("charlie");
        dave    = saveUser("dave");
        eve     = saveUser("eve");
    }

    @Test
    void scoreCalculatesCorrectlyFromMutualFriendsAndSharedGroups() {
        makeFriends(alice, bob);
        makeFriends(alice, charlie);

        makeFriends(dave, bob);
        makeFriends(dave, charlie);

        GroupResponse group = groupService.createGroup(principal(alice),
                new GroupCreateRequest("Hikers", "Hiking", GroupType.PUBLIC));
        groupService.joinGroup(principal(dave), group.getId());

        List<FriendRecommendationDto> recs = friendshipService.getSuggestedFriends(principal(alice));

        assertThat(recs).isNotEmpty();
        FriendRecommendationDto daveRec = recs.stream()
                .filter(r -> r.getUsername().equals("dave"))
                .findFirst()
                .orElseThrow();

        assertThat(daveRec.getMutualFriends()).isEqualTo(2);
        assertThat(daveRec.getSharedGroups()).isEqualTo(1);
        assertThat(daveRec.getScore()).isEqualTo(13);
    }

    @Test
    void existingFriendsAndSelfAreExcludedFromRecommendations() {
        makeFriends(alice, bob);
        makeFriends(bob, charlie);

        List<FriendRecommendationDto> recs = friendshipService.getSuggestedFriends(principal(alice));

        assertThat(recs).noneMatch(r -> r.getUsername().equals("alice"));
        assertThat(recs).noneMatch(r -> r.getUsername().equals("bob"));
        assertThat(recs).anyMatch(r -> r.getUsername().equals("charlie"));
    }

    @Test
    void recommendationsAreOrderedByScoreDescending() {
        makeFriends(alice, bob);
        makeFriends(alice, charlie);

        makeFriends(dave, bob);
        makeFriends(dave, charlie);

        makeFriends(eve, bob);

        List<FriendRecommendationDto> recs = friendshipService.getSuggestedFriends(principal(alice));

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0).getUsername()).isEqualTo("dave");
        assertThat(recs.get(0).getScore()).isEqualTo(10);
        assertThat(recs.get(1).getUsername()).isEqualTo("eve");
        assertThat(recs.get(1).getScore()).isEqualTo(5);
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .role(Role.USER)
                .build());
    }

    private void makeFriends(User a, User b) {
        friendshipRepository.save(Friendship.builder()
                .requester(a).addressee(b).status(FriendshipStatus.ACCEPTED).build());
    }

    private static Principal principal(User u) {
        return u::getUsername;
    }
}
