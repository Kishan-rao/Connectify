package com.example.backend.repository;

import com.example.backend.entity.Friendship;
import com.example.backend.entity.FriendshipStatus;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) AND f.status = com.example.backend.entity.FriendshipStatus.ACCEPTED")
    List<Friendship> findAllAcceptedFriendships(@Param("user") User user);

    @Query("SELECT f FROM Friendship f WHERE f.addressee = :user AND f.status = com.example.backend.entity.FriendshipStatus.PENDING")
    List<Friendship> findPendingRequests(@Param("user") User user);

    boolean existsByRequesterAndAddressee(User requester, User addressee);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester = :user1 AND f.addressee = :user2) OR " +
           "(f.requester = :user2 AND f.addressee = :user1)")
    Optional<Friendship> findBetween(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
           "WHERE ((f.requester = :user1 AND f.addressee = :user2) OR (f.requester = :user2 AND f.addressee = :user1)) " +
           "AND f.status = com.example.backend.entity.FriendshipStatus.ACCEPTED")
    boolean areFriends(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT CASE WHEN f.requester.id = :userId THEN f.addressee.id ELSE f.requester.id END " +
           "FROM Friendship f " +
           "WHERE (f.requester.id = :userId OR f.addressee.id = :userId) AND f.status = com.example.backend.entity.FriendshipStatus.ACCEPTED")
    List<UUID> findFriendIds(@Param("userId") UUID userId);

    @Query("SELECT f.addressee.id FROM Friendship f WHERE f.requester.id = :userId AND f.status = com.example.backend.entity.FriendshipStatus.PENDING")
    List<UUID> findPendingSentAddresseeIds(@Param("userId") UUID userId);

    @Query("SELECT f.requester.id FROM Friendship f WHERE f.addressee.id = :userId AND f.status = com.example.backend.entity.FriendshipStatus.PENDING")
    List<UUID> findPendingReceivedRequesterIds(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Friendship f WHERE f.requester = :user OR f.addressee = :user")
    void deleteByUserAsRequesterOrAddressee(@Param("user") User user);
}
