package com.example.gitbot.dto;

import java.util.List;

public record RetrievedContextDto(
        List<CitationDto> citations,
        String contextText,
        long vectorSearchDurationMs,
        long neighborDurationMs,
        long ragTotalDurationMs
) {
    public RetrievedContextDto(List<CitationDto> citations, String contextText) {
        this(citations, contextText, 0, 0, 0);
    }
}
