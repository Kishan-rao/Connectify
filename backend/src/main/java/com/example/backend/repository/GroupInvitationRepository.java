package com.example.backend.repository;

import com.example.backend.entity.Group;
import com.example.backend.entity.GroupInvitation;
import com.example.backend.entity.InvitationStatus;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupInvitationRepository extends JpaRepository<GroupInvitation, UUID> {

    boolean existsByGroupAndInviteeAndStatus(Group group, User invitee, InvitationStatus status);

    @EntityGraph(attributePaths = {"group", "inviter"})
    List<GroupInvitation> findByInviteeAndStatusOrderByCreatedAtDesc(User invitee, InvitationStatus status);

    @Modifying
    @Query("DELETE FROM GroupInvitation gi WHERE gi.invitee = :user OR gi.inviter = :user")
    void deleteByUserAsInviteeOrInviter(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM GroupInvitation gi WHERE gi.group = :group")
    void deleteByGroup(@Param("group") Group group);
}
