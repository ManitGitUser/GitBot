package com.example.gitbot.dto;

public record SyncAllReposResponse(
        int totalRepositories,
        int newRepositories,
        int updatedRepositories,
        int unchangedRepositories,
        int repositoriesWithNewCommits
) {
}
