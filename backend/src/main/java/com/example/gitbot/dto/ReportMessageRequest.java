package com.example.gitbot.dto;

import com.example.gitbot.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportMessageRequest(
        @NotNull(message = "Reason is required")
        ReportReason reason,
        @Size(max = 2000, message = "Details cannot exceed 2000 characters")
        String details) {
}
