package com.example.gitbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DemoChatRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 1000, message = "Message exceeds maximum length of 1000 characters")
        String message,
        List<DemoChatMessageDto> history,
        String demoToken
) {
}
