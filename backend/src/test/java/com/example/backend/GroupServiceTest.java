package com.example.backend;

import com.example.backend.dto.*;
import com.example.backend.entity.*;
import com.example.backend.repository.*;
import com.example.backend.service.GroupService;
import com.example.backend.service.NotificationService;
import com.example.backend.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class GroupServiceTest {

    @Autowired GroupService groupService;
    @Autowired PostService postService;
    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMembershipRepository groupMembershipRepository;
    @Autowired GroupInvitationRepository groupInvitationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User alice, bob, charlie;

    @BeforeEach
    void setUp() {
        alice   = saveUser("alice");
        bob     = saveUser("bob");
        charlie = saveUser("charlie");
    }

    // ── Existing tests (preserved) ─────────────────────────────────────────

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

    // ── Privacy tests ──────────────────────────────────────────────────────

    @Test
    void nonMember_viewingPrivateGroup_seesOnlyIdNameType() {
        GroupResponse created = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("TopSecret", "Very secret group", GroupType.PRIVATE)
        );
        // bob is not a member
        GroupResponse bobView = groupService.getGroup(principal(bob), created.getId());

        assertThat(bobView.getId()).isEqualTo(created.getId());
        assertThat(bobView.getName()).isEqualTo("TopSecret");
        assertThat(bobView.getType()).isEqualTo(GroupType.PRIVATE);
        assertThat(bobView.getDescription()).isNull();
        assertThat(bobView.getMemberCount()).isNull();
        assertThat(bobView.getCreatedBy()).isNull();
    }

    @Test
    void nonMember_viewingClosedGroup_seesOnlyIdNameType() {
        GroupResponse created = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("ClosedGroup", "Closed", GroupType.CLOSED)
        );
        GroupResponse bobView = groupService.getGroup(principal(bob), created.getId());

        assertThat(bobView.getId()).isEqualTo(created.getId());
        assertThat(bobView.getName()).isEqualTo("ClosedGroup");
        assertThat(bobView.getType()).isEqualTo(GroupType.CLOSED);
        assertThat(bobView.getDescription()).isNull();
        assertThat(bobView.getMemberCount()).isNull();
        assertThat(bobView.getCreatedBy()).isNull();
    }

    @Test
    void member_viewingPrivateGroup_seesFullDetails() {
        GroupResponse created = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("AlicesSecret", "Alice secret", GroupType.PRIVATE)
        );
        // alice is a member (creator)
        GroupResponse aliceView = groupService.getGroup(principal(alice), created.getId());

        assertThat(aliceView.getId()).isEqualTo(created.getId());
        assertThat(aliceView.getName()).isEqualTo("AlicesSecret");
        assertThat(aliceView.getType()).isEqualTo(GroupType.PRIVATE);
        assertThat(aliceView.getDescription()).isEqualTo("Alice secret");
        assertThat(aliceView.getMemberCount()).isEqualTo(1L);
        assertThat(aliceView.getCreatedBy()).isNotNull();
    }

    @Test
    void listGroups_nonMemberSeesRestrictedPrivateGroupInfo() {
        groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("HiddenGroup", "Hidden description", GroupType.PRIVATE)
        );
        // bob not a member; list groups as bob
        PagedResponse<GroupResponse> list = groupService.listGroups(principal(bob), 0, 20);
        GroupResponse hiddenGroup = list.getContent().stream()
                .filter(g -> g.getName().equals("HiddenGroup"))
                .findFirst().orElseThrow();

        assertThat(hiddenGroup.getDescription()).isNull();
        assertThat(hiddenGroup.getMemberCount()).isNull();
    }

    // ── Invitation tests ───────────────────────────────────────────────────

    @Test
    void member_canInvite_existingNonMember() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("PrivateClub", "Private", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        assertThat(inv.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(inv.getInviter().getUsername()).isEqualTo("alice");
        assertThat(inv.getGroupName()).isEqualTo("PrivateClub");
    }

    @Test
    void invitedUser_hasPendingInvitation() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("PendingTest", "desc", GroupType.PRIVATE)
        );
        groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("bob"));

        List<GroupInvitationResponse> invites = groupService.getMyPendingInvitations(principal(bob));
        assertThat(invites).hasSize(1);
        assertThat(invites.get(0).getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invites.get(0).getInviter().getUsername()).isEqualTo("alice");
    }

    @Test
    void duplicatePendingInvitation_isRejected() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DupTest", "desc", GroupType.PRIVATE)
        );
        groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("bob"));

        assertThatThrownBy(() ->
                groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending invitation");
    }

    @Test
    void nonMember_cannotInvite() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("NonMemberTest", "desc", GroupType.PRIVATE)
        );
        // bob is NOT a member
        assertThatThrownBy(() ->
                groupService.inviteUser(principal(bob), group.getId(), new GroupInviteRequest("charlie")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invitingNonexistentUser_throwsNotFound() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("NotFoundTest", "desc", GroupType.PRIVATE)
        );
        assertThatThrownBy(() ->
                groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("doesnotexist")))
                .isInstanceOf(com.example.backend.exception.ResourceNotFoundException.class);
    }

    @Test
    void invitingAlreadyMember_isRejected() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("AlreadyMemberTest", "desc", GroupType.PUBLIC)
        );
        groupService.joinGroup(principal(bob), group.getId());

        assertThatThrownBy(() ->
                groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("bob")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void selfInvitation_isRejected() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("SelfInviteTest", "desc", GroupType.PRIVATE)
        );
        assertThatThrownBy(() ->
                groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("alice")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invitee_canAccept_andMembershipCreated() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("AcceptTest", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        groupService.acceptInvitation(principal(bob), inv.getId());

        // membership exists
        Group groupEntity = groupRepository.findById(group.getId()).orElseThrow();
        User bobEntity = userRepository.findByUsername("bob").orElseThrow();
        assertThat(groupMembershipRepository.existsByGroupAndUser(groupEntity, bobEntity)).isTrue();

        // status is ACCEPTED
        GroupInvitation savedInv = groupInvitationRepository.findById(inv.getId()).orElseThrow();
        assertThat(savedInv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    void accepting_doesNotCreateDuplicateMembership() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DupMemberTest", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        // Accept once
        groupService.acceptInvitation(principal(bob), inv.getId());

        // Try to accept again - should fail (not PENDING)
        assertThatThrownBy(() -> groupService.acceptInvitation(principal(bob), inv.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer pending");

        // Verify only one membership
        Group groupEntity = groupRepository.findById(group.getId()).orElseThrow();
        User bobEntity = userRepository.findByUsername("bob").orElseThrow();
        long count = groupMembershipRepository.findByGroupOrderByJoinedAtAsc(groupEntity).stream()
                .filter(m -> m.getUser().getId().equals(bobEntity.getId()))
                .count();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void nonInvitee_cannotAccept() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("NonInviteeAccept", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        // charlie tries to accept bob's invitation
        assertThatThrownBy(() -> groupService.acceptInvitation(principal(charlie), inv.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invitee_canDecline_noMembershipCreated() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DeclineTest", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        groupService.declineInvitation(principal(bob), inv.getId());

        Group groupEntity = groupRepository.findById(group.getId()).orElseThrow();
        User bobEntity = userRepository.findByUsername("bob").orElseThrow();
        assertThat(groupMembershipRepository.existsByGroupAndUser(groupEntity, bobEntity)).isFalse();

        GroupInvitation savedInv = groupInvitationRepository.findById(inv.getId()).orElseThrow();
        assertThat(savedInv.getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    void declining_setsStatusDeclined() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DeclineStatusTest", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));
        groupService.declineInvitation(principal(bob), inv.getId());

        GroupInvitation savedInv = groupInvitationRepository.findById(inv.getId()).orElseThrow();
        assertThat(savedInv.getStatus()).isEqualTo(InvitationStatus.DECLINED);
    }

    @Test
    void nonInvitee_cannotDecline() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("NonInviteeDecline", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));

        assertThatThrownBy(() -> groupService.declineInvitation(principal(charlie), inv.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void alreadyDeclinedInvitation_cannotBeDeclinedAgain() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("DeclineAgain", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));
        groupService.declineInvitation(principal(bob), inv.getId());

        assertThatThrownBy(() -> groupService.declineInvitation(principal(bob), inv.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void alreadyAcceptedInvitation_cannotBeAcceptedAgain() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("AcceptAgain", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));
        groupService.acceptInvitation(principal(bob), inv.getId());

        assertThatThrownBy(() -> groupService.acceptInvitation(principal(bob), inv.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer pending");
    }

    @Test
    void afterDecline_newInvitationCanBeCreated() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("ReInviteTest", "desc", GroupType.PRIVATE)
        );
        GroupInvitationResponse inv = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));
        groupService.declineInvitation(principal(bob), inv.getId());

        // New invitation can be created after decline
        GroupInvitationResponse inv2 = groupService.inviteUser(
                principal(alice), group.getId(), new GroupInviteRequest("bob"));
        assertThat(inv2.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void inviteUser_createsGroupInviteNotification() {
        GroupResponse group = groupService.createGroup(
                principal(alice),
                new GroupCreateRequest("NotifTest", "desc", GroupType.PRIVATE)
        );
        groupService.inviteUser(principal(alice), group.getId(), new GroupInviteRequest("bob"));

        // bob should have a GROUP_INVITE notification
        PagedResponse<NotificationResponse> notifs = notificationService.getMyNotifications(principal(bob), 0, 10);
        assertThat(notifs.getContent()).hasSize(1);
        assertThat(notifs.getContent().get(0).getType()).isEqualTo(NotificationType.GROUP_INVITE);
        assertThat(notifs.getContent().get(0).getActor().getUsername()).isEqualTo("alice");
        assertThat(notifs.getContent().get(0).getMessage()).contains("NotifTest");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
