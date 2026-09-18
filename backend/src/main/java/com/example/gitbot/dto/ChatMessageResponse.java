package com.example.gitbot.dto;

import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record ChatMessageResponse(
        UUID id,
        MessageRole role,
        MessageStatus status,
        String content,
        List<CitationDto> citations,
        Instant createdAt) {
}