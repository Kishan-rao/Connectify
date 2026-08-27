package com.example.backend.repository;

import com.example.backend.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByName(String name);

    boolean existsByName(String name);

    @EntityGraph(attributePaths = {"createdBy"})
    Page<Group> findByOrderByCreatedAtDesc(Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Group g WHERE g.createdBy = :user")
    void deleteByCreatedBy(@org.springframework.data.repository.query.Param("user") com.example.backend.entity.User user);
}
