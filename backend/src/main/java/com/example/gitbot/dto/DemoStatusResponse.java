package com.example.gitbot.dto;

public record DemoStatusResponse(
        boolean enabled,
        String repoName,
        String repoFullName,
        int maxMessages,
        int remainingMessages
) {
    public DemoStatusResponse(boolean enabled, String repoName, String repoFullName, int maxMessages) {
        this(enabled, repoName, repoFullName, maxMessages, maxMessages);
    }
}
