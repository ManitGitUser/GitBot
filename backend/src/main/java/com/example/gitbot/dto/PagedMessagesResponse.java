package com.example.gitbot.dto;

import java.util.List;

public record PagedMessagesResponse(
        List<ChatMessageResponse> messages,
        boolean hasMore,
        String nextCursor
) {
}
