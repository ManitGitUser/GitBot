package com.example.gitbot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BranchChatSessionRequest(
        @NotNull(message = "Message ID is required")
        UUID messageId,
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title) {
}
