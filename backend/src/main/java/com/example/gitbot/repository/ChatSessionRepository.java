package com.example.gitbot.repository;

import com.example.gitbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByUserIdAndRepositoryIdOrderByCreatedAtDesc(UUID userId, UUID repositoryId);

    Page<ChatSession> findByUserIdAndRepositoryIdOrderByCreatedAtDesc(UUID userId, UUID repositoryId, Pageable pageable);

    List<ChatSession> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    List<ChatSession> findByUserId(UUID userId);

    Optional<ChatSession> findByShareTokenAndIsSharedTrue(String shareToken);
}
