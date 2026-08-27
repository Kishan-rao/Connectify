package com.example.backend;

import com.example.backend.dto.GroupCreateRequest;
import com.example.backend.dto.GroupResponse;
import com.example.backend.dto.PostCreateRequest;
import com.example.backend.dto.PostResponse;
import com.example.backend.dto.PagedResponse;
import com.example.backend.entity.GroupType;
import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.repository.GroupMembershipRepository;
import com.example.backend.repository.GroupRepository;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.GroupService;
import com.example.backend.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GroupServiceTest {

    @Autowired GroupService groupService;
    @Autowired PostService postService;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, charlie;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob   = saveUser("bob");
        charlie = saveUser("charlie");
    }

    @Test
    void createGroup_creatorAutomaticallyBecomesMember() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("TechTalks", "Tech discussions", GroupType.PUBLIC)
        );

        assertThat(group.getName()).isEqualTo("TechTalks");
        assertThat(group.isMember()).isTrue();
        assertThat(group.getMemberCount()).isEqualTo(1L);
    }

    @Test
    void joinAndLeaveGroup() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("Photography", "Photos", GroupType.PUBLIC)
        );

        groupService.joinGroup(principal(bob), group.getId());
        GroupResponse afterJoin = groupService.getGroup(principal(bob), group.getId());
        assertThat(afterJoin.isMember()).isTrue();
        assertThat(afterJoin.getMemberCount()).isEqualTo(2L);

        groupService.leaveGroup(principal(bob), group.getId());
        GroupResponse afterLeave = groupService.getGroup(principal(bob), group.getId());
        assertThat(afterLeave.isMember()).isFalse();
        assertThat(afterLeave.getMemberCount()).isEqualTo(1L);
    }

    @Test
    void duplicateJoinGroup_throwsConflictException() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("Runners", "Running", GroupType.PUBLIC)
        );

        assertThatThrownBy(() -> groupService.joinGroup(principal(alice), group.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void groupPost_canOnlyBeCreatedByMember() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DevOps", "DevOps", GroupType.PUBLIC)
        );

        PostCreateRequest validReq = PostCreateRequest.builder()
                .content("Welcome to DevOps group!")
                .groupId(group.getId())
                .build();
        PostResponse post = postService.createPost(principal(alice), validReq);
        assertThat(post.getGroup().getName()).isEqualTo("DevOps");

        PostCreateRequest invalidReq = PostCreateRequest.builder()
                .content("Unauthorized post")
                .groupId(group.getId())
                .build();
        assertThatThrownBy(() -> postService.createPost(principal(bob), invalidReq))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void privateGroupPosts_cannotBeViewedByNonMembers() {
        GroupResponse privateGroup = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("SecretClub", "Secret", GroupType.PRIVATE)
        );

        postService.createPost(principal(alice), PostCreateRequest.builder()
                .content("Secret post")
                .groupId(privateGroup.getId())
                .build());

        PagedResponse<PostResponse> aliceView = groupService.getGroupPosts(principal(alice), privateGroup.getId(), 0, 10);
        assertThat(aliceView.getContent()).hasSize(1);

        assertThatThrownBy(() -> groupService.getGroupPosts(principal(bob), privateGroup.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    private User saveUser(String username) {
        return userRepository.save(User.builder()
                .username(username)
                .email(username + "@test.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .role(Role.USER)
                .build());
    }

    private static Principal principal(User u) {
        return u::getUsername;
    }
}
