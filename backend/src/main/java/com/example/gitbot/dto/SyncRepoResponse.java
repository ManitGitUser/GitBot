package com.example.gitbot.dto;

import java.util.UUID;

public record SyncRepoResponse(
        UUID repoId,
        boolean reindexTriggered,
        String message,
        String currentCommitSha,
        String indexedCommitSha
) {
}
