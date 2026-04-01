package com.example.backend.repository;

import com.example.backend.entity.Group;
import com.example.backend.entity.GroupMembership;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {

    /**
     * Finds all groups that both user1 and user2 share as members.
     */
    @Query("SELECT gm.group FROM GroupMembership gm WHERE gm.user = :user1 " +
           "AND gm.group IN (SELECT gm2.group FROM GroupMembership gm2 WHERE gm2.user = :user2)")
    List<Group> findMutualGroups(@Param("user1") User user1, @Param("user2") User user2);

    /**
     * Finds all group memberships for a given user.
     */
    List<GroupMembership> findByUser(User user);
}
