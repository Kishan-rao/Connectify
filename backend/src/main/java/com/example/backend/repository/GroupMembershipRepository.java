package com.example.backend.repository;

import com.example.backend.entity.Group;
import com.example.backend.entity.GroupMembership;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

    /**
     * Finds all groups that both user1 and user2 share as members.
     */
    @Query("SELECT gm.group FROM GroupMembership gm WHERE gm.user = :user1 " +
           "AND gm.group IN (SELECT gm2.group FROM GroupMembership gm2 WHERE gm2.user = :user2)")
    List<Group> findMutualGroups(@Param("user1") User user1, @Param("user2") User user2);

    boolean existsByGroupAndUser(Group group, User user);

    java.util.Optional<GroupMembership> findByGroupAndUser(Group group, User user);

    long countByGroup(Group group);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    List<GroupMembership> findByGroupOrderByJoinedAtAsc(Group group);

    /**
     * Finds all group memberships for a given user.
     */
    List<GroupMembership> findByUser(User user);

    /**
     * Returns all group IDs that the given user belongs to.
     * Single bulk query — avoids per-post group lookups in the feed.
     */
    @Query("SELECT gm.group.id FROM GroupMembership gm WHERE gm.user.id = :userId")
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);

    /**
     * Returns the UUIDs of all users (excluding the given user) who share at least
     * one group with that user. Single bulk query used for feed inclusion and explanation.
     */
    @Query("SELECT DISTINCT gm2.user.id FROM GroupMembership gm " +
           "JOIN GroupMembership gm2 ON gm.group = gm2.group " +
           "WHERE gm.user.id = :userId AND gm2.user.id <> :userId")
    List<UUID> findGroupMemberUserIds(@Param("userId") UUID userId);

    /**
     * For a given set of group member user IDs, returns a mapping of userId → first shared group name.
     * Used to generate accurate feed explanations without per-post queries.
     * Returns Object[] rows: [userId, groupName]
     */
    @Query("SELECT gm2.user.id, MIN(gm2.group.name) FROM GroupMembership gm " +
           "JOIN GroupMembership gm2 ON gm.group = gm2.group " +
           "WHERE gm.user.id = :userId AND gm2.user.id IN :memberIds " +
           "GROUP BY gm2.user.id")
    List<Object[]> findSharedGroupNamesByUserIds(@Param("userId") UUID userId,
                                                  @Param("memberIds") List<UUID> memberIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM GroupMembership gm WHERE gm.user = :user")
    void deleteByUser(@Param("user") User user);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM GroupMembership gm WHERE gm.group IN (SELECT g FROM Group g WHERE g.createdBy = :user)")
    void deleteByGroupCreator(@Param("user") User user);
}

