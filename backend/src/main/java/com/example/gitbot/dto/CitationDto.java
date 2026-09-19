package com.example.gitbot.dto;

public record CitationDto(
        String filePath,
        Integer startLine,
        Integer endLine,
        String language,
        String repoFullName) {
    public CitationDto(String filePath, Integer startLine, Integer endLine, String language) {
        this(filePath, startLine, endLine, language, null);
    }
}
