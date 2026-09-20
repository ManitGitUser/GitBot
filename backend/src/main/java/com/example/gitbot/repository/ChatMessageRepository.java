package com.example.gitbot.repository;

import com.example.gitbot.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<ChatMessage> findByIdAndSessionId(UUID id, UUID sessionId);

    List<ChatMessage> findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(UUID sessionId, Instant createdAt);

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.sessionId = :sessionId
        ORDER BY m.createdAt DESC, m.id DESC
    """)
    List<ChatMessage> findLatestMessages(
            @Param("sessionId") UUID sessionId,
            Pageable pageable
    );

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.sessionId = :sessionId
          AND (m.createdAt < :beforeCreatedAt OR (m.createdAt = :beforeCreatedAt AND m.id < :beforeId))
        ORDER BY m.createdAt DESC, m.id DESC
    """)
    List<ChatMessage> findMessagesBefore(
            @Param("sessionId") UUID sessionId,
            @Param("beforeCreatedAt") Instant beforeCreatedAt,
            @Param("beforeId") UUID beforeId,
            Pageable pageable
    );

    @Query("""
        SELECT m FROM ChatMessage m
        WHERE m.sessionId = :sessionId
          AND (m.createdAt > :afterCreatedAt OR (m.createdAt = :afterCreatedAt AND m.id > :afterId))
        ORDER BY m.createdAt ASC, m.id ASC
    """)
    List<ChatMessage> findMessagesAfter(
            @Param("sessionId") UUID sessionId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable
    );

    void deleteBySessionId(UUID sessionId);
}