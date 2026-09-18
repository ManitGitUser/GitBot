package com.example.gitbot.repository;

import com.example.gitbot.entity.MessageReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {
    boolean existsByUserIdAndMessageId(UUID userId, UUID messageId);
    void deleteBySessionId(UUID sessionId);
}
