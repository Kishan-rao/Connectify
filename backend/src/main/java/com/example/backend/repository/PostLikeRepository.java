package com.example.backend.repository;

import com.example.backend.entity.Post;
import com.example.backend.entity.PostLike;
import com.example.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostAndUser(Post post, User user);

    Optional<PostLike> findByPostAndUser(Post post, User user);

    long countByPost(Post post);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post = :post")
    void deleteByPost(@Param("post") Post post);

    List<PostLike> findByPostOrderByCreatedAtDesc(Post post);

    @Query("SELECT pl.post.id, COUNT(pl) FROM PostLike pl WHERE pl.post.id IN :postIds GROUP BY pl.post.id")
    List<Object[]> countLikesByPostIds(@Param("postIds") List<UUID> postIds);

    @Query("SELECT pl.post.id FROM PostLike pl WHERE pl.user.id = :userId AND pl.post.id IN :postIds")
    List<UUID> findLikedPostIdsByUserIdAndPostIds(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.user = :user")
    void deleteByUser(@Param("user") User user);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post IN (SELECT p FROM Post p WHERE p.user = :user)")
    void deleteByPostAuthor(@Param("user") User user);
}
