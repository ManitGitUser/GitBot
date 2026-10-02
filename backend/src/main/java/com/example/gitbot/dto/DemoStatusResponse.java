package com.example.gitbot.dto;

public record DemoStatusResponse(
        boolean enabled,
        String repoName,
        String repoFullName,
        int maxMessages
) {
}
