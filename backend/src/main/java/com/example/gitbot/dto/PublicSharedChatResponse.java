package com.example.gitbot.dto;

import java.time.Instant;
import java.util.List;

public record PublicSharedChatResponse(
        String title,
        String repoFullName,
        Instant sharedAt,
        List<ChatMessageResponse> messages) {
}
