package com.example.gitbot.repository;

import com.example.gitbot.entity.GitRepo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;


public interface GitRepoRepository extends JpaRepository<GitRepo, UUID> {

    List<GitRepo> findByUserIdOrderByFullNameAsc(UUID userId);

    Optional<GitRepo> findByIdAndUserId(UUID id, UUID userId);

    Optional<GitRepo> findByUserIdAndGithubRepoId(UUID userId, Long githubRepoId);
 
    List<GitRepo> findByIndexStatus(com.example.gitbot.enums.IndexStatus indexStatus);
}
