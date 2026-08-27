package com.example.backend.repository;

import com.example.backend.entity.Notification;
import com.example.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = {"actor", "post", "friendship"})
    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFalse(User recipient);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient = :recipient AND n.read = false")
    int markAllAsReadForRecipient(@Param("recipient") User recipient);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient = :user OR n.actor = :user")
    void deleteByUserAsRecipientOrActor(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.post IN (SELECT p FROM Post p WHERE p.user = :user)")
    void deleteByPostAuthor(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.friendship IN (SELECT f FROM Friendship f WHERE f.requester = :user OR f.addressee = :user)")
    void deleteByFriendshipUser(@Param("user") User user);
}
