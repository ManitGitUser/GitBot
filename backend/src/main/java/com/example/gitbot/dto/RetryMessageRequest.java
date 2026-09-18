package com.example.gitbot.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RetryMessageRequest(
        @NotNull(message = "Message ID is required")
        UUID messageId) {
}
