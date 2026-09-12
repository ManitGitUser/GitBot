package com.example.gitbot.dto;

import java.util.UUID;

public record UserResponse(
        UUID id,
        Long githubId,
        String githubUsername,
        String displayName,
        String avatarUrl
) {
}
