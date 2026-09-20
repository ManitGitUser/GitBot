package com.example.gitbot.repository;

import com.example.gitbot.entity.GitRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface GitRepoRepository extends JpaRepository<GitRepo, UUID> {

    List<GitRepo> findByUserId(UUID userId);

    Optional<GitRepo> findByIdAndUserId(UUID id, UUID userId);

    Optional<GitRepo> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
 
    List<GitRepo> findByIndexStatus(com.example.gitbot.enums.IndexStatus indexStatus);

    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM GitRepo r
        WHERE r.userId = :userId
          AND (:isPrivate IS NULL OR r.isPrivate = :isPrivate)
          AND (:status IS NULL OR r.indexStatus = :status)
          AND (:search IS NULL OR :search = '' OR
               LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(COALESCE(r.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(COALESCE(r.language, '')) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY r.fullName ASC
    """)
    Page<GitRepo> findWithFilters(
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("isPrivate") Boolean isPrivate,
            @org.springframework.data.repository.query.Param("status") com.example.gitbot.enums.IndexStatus status,
            @org.springframework.data.repository.query.Param("search") String search,
            Pageable pageable
    );
}
