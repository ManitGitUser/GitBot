package com.example.gitbot.dto;

import java.util.List;

public record RetrievedContextDto(
        List<CitationDto> citations,
        String contextText
) {}
