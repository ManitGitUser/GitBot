package com.example.gitbot.service.ai;

import java.util.List;

import com.example.gitbot.dto.CitationDto;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CitationMapper {

    private final JsonMapper jsonMapper;

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public CitationDto fromDocument(Document document) {
        var meta = document.getMetadata();
        return new CitationDto(
                stringVal(meta.get("filePath")),
                intVal(meta.get("startLine")),
                intVal(meta.get("endLine")),
                stringVal(meta.get("language"))
        );
    }

    public String toJson(List<CitationDto> citations) {
        try {
            return jsonMapper.writeValueAsString(citations);
        } catch (JacksonException e) {
            return "[]";
        }
    }

    public List<CitationDto> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
