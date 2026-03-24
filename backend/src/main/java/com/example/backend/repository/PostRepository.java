package com.example.backend.repository;

import com.example.backend.entity.Post;
import com.example.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    Page<Post> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    long countByUser(User user);

    @Query("SELECT p FROM Post p WHERE p.user IN :users OR p.user = :currentUser ORDER BY p.createdAt DESC")
    Page<Post> findFeed(@Param("users") List<User> users, @Param("currentUser") User currentUser, Pageable pageable);
}
