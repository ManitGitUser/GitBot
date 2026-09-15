package com.example.gitbot.dto;

import com.example.gitbot.enums.IndexStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record GitRepoResponse(
        UUID id,
        Long githubRepoId,
        String owner,
        String name,
        String fullName,
        @JsonProperty("isPrivate") boolean isPrivate,
        String defaultBranch,
        String language,
        String htmlUrl,
        String description,
        IndexStatus indexStatus,
        Instant indexedAt,
        int chunkCount,
        int filesTotal,
        int filesProcessed,
        String errorMessage
) {
}
