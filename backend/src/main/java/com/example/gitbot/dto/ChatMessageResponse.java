package com.example.gitbot.dto;

import com.example.gitbot.enums.MessageRole;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public record ChatMessageResponse(
        UUID id,
        MessageRole role,
        String content,
        List<CitationDto> citations,
        Instant createdAt) {
}