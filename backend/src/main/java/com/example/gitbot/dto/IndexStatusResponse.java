package com.example.gitbot.dto;

import com.example.gitbot.enums.IndexStatus;

import java.time.Instant;
import java.util.UUID;

public record IndexStatusResponse(
        UUID repositoryId,
        IndexStatus indexStatus,
        int filesTotal,
        int filesProcessed,
        int chunkCount,
        Instant indexedAt,
        String errorMessage
) {
}
