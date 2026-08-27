package com.example.backend.repository;

import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @EntityGraph(attributePaths = {"user", "group"})
    Page<Post> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    long countByUser(User user);

    @Query("SELECT p FROM Post p WHERE p.user IN :users OR p.user = :currentUser ORDER BY p.createdAt DESC")
    Page<Post> findFeed(@Param("users") List<User> users, @Param("currentUser") User currentUser, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "group"})
    @Query("SELECT DISTINCT p FROM Post p WHERE p.user.id IN :authorIds ORDER BY p.createdAt DESC")
    Page<Post> findFeedByAuthorIds(@Param("authorIds") List<UUID> authorIds, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "group"})
    Page<Post> findByGroupOrderByCreatedAtDesc(com.example.backend.entity.Group group, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "group"})
    @Query("SELECT DISTINCT p FROM Post p WHERE " +
           "(p.user.id IN :authorIds AND p.group IS NULL) OR " +
           "(p.group.id IN :groupIds) " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findUnifiedFeed(@Param("authorIds") List<UUID> authorIds,
                               @Param("groupIds") List<UUID> groupIds,
                               Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Post p WHERE p.user = :user")
    void deleteByUser(@Param("user") User user);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM Post p WHERE p.group IN (SELECT g FROM Group g WHERE g.createdBy = :user)")
    void deleteByGroupCreator(@Param("user") User user);
}
