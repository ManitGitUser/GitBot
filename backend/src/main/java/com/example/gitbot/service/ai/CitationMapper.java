package com.example.gitbot.service.ai;

import java.util.List;

import com.example.gitbot.dto.CitationDto;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import lombok.RequiredArgsConstructor;

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

    private static final java.util.Set<String> GENERIC_FILE_NAMES = java.util.Set.of(
            "page.tsx", "page.jsx", "page.js", "page.ts",
            "layout.tsx", "layout.jsx", "layout.js", "layout.ts",
            "route.ts", "route.js",
            "index.ts", "index.js", "index.tsx", "index.jsx",
            "types.ts", "types.d.ts",
            "style.css", "styles.css", "global.css", "globals.css",
            "loading.tsx", "error.tsx", "not-found.tsx",
            "constants.ts", "utils.ts", "api.ts"
    );

    private static final java.util.Set<String> NON_DISTINCTIVE_BASE_NAMES = java.util.Set.of(
            "test", "tests", "main", "base", "app", "view", "data", "list",
            "icon", "card", "form", "item", "user", "file", "code", "info",
            "rule", "auth", "state", "step", "node", "link", "modal", "page"
    );

    public CitationDto fromDocument(Document document) {
        var meta = document.getMetadata();
        return new CitationDto(
                stringVal(meta.get("filePath")),
                intVal(meta.get("startLine")),
                intVal(meta.get("endLine")),
                stringVal(meta.get("language")),
                stringVal(meta.get("repoFullName"))
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

    public List<CitationDto> filterSupportingCitations(String reply, List<CitationDto> candidateCitations) {
        if (candidateCitations == null || candidateCitations.isEmpty() || reply == null || reply.isBlank()) {
            return List.of();
        }

        if (isInsufficientOrIrrelevant(reply)) {
            return List.of();
        }

        if (isCasualConversation(reply, candidateCitations)) {
            return List.of();
        }

        java.util.List<CitationDto> supporting = new java.util.ArrayList<>();
        for (CitationDto citation : candidateCitations) {
            if (isCitationReferenced(reply, citation)) {
                supporting.add(citation);
            }
        }

        return supporting;
    }

    public boolean isInsufficientOrIrrelevant(String reply) {
        if (reply == null || reply.isBlank()) {
            return true;
        }
        String lower = reply.toLowerCase(java.util.Locale.ROOT);

        if (lower.contains("provided context") || lower.contains("provided code context")
                || lower.contains("provided repository") || lower.contains("given context")
                || lower.contains("the context provided")) {
            if (lower.contains("does not contain")
                    || lower.contains("doesn't contain")
                    || lower.contains("does not mention")
                    || lower.contains("doesn't mention")
                    || lower.contains("does not have")
                    || lower.contains("doesn't have")
                    || lower.contains("does not provide")
                    || lower.contains("doesn't provide")
                    || lower.contains("does not include")
                    || lower.contains("doesn't include")
                    || lower.contains("is insufficient")
                    || lower.contains("are insufficient")
                    || lower.contains("no information")
                    || lower.contains("no relevant")
                    || lower.contains("not contain any")
                    || lower.contains("cannot find")
                    || lower.contains("could not find")
                    || lower.contains("not found")
                    || lower.contains("not mentioned")) {
                return true;
            }
        }

        if (lower.contains("context does not contain")
                || lower.contains("context doesn't contain")
                || lower.contains("context is insufficient")
                || lower.contains("no relevant source code")
                || lower.contains("no relevant code")
                || lower.contains("not contain any information or relevant source code")
                || lower.contains("no information or relevant source code")) {
            return true;
        }

        return false;
    }

    public boolean isCasualConversation(String reply, List<CitationDto> candidateCitations) {
        if (reply == null || reply.isBlank()) {
            return false;
        }
        if (reply.contains("```")) {
            return false;
        }
        if (candidateCitations != null) {
            for (CitationDto c : candidateCitations) {
                if (isCitationReferenced(reply, c)) {
                    return false;
                }
            }
        }

        String lower = reply.toLowerCase(java.util.Locale.ROOT).trim();
        boolean hasGreeting = lower.startsWith("hello")
                || lower.startsWith("hi ")
                || lower.startsWith("hi!")
                || lower.startsWith("hi,")
                || lower.startsWith("hey ")
                || lower.startsWith("hey!")
                || lower.startsWith("hey,")
                || lower.startsWith("good morning")
                || lower.startsWith("good afternoon")
                || lower.startsWith("good evening");

        boolean hasAssistantIntro = lower.contains("i am gitbot")
                || lower.contains("i'm gitbot")
                || lower.contains("how can i help you")
                || lower.contains("how can i assist you")
                || lower.contains("feel free to ask")
                || lower.contains("i'm doing well")
                || lower.contains("i am doing well")
                || lower.contains("you're welcome")
                || lower.contains("you are welcome")
                || lower.contains("glad to help");

        return hasGreeting || hasAssistantIntro;
    }

    public boolean isCitationReferenced(String reply, CitationDto citation) {
        if (reply == null || reply.isBlank() || citation == null || citation.filePath() == null) {
            return false;
        }

        String filePath = citation.filePath().trim();
        if (filePath.isBlank()) {
            return false;
        }

        String lowerReply = reply.toLowerCase(java.util.Locale.ROOT);
        String lowerPath = filePath.toLowerCase(java.util.Locale.ROOT);

        // 1. Full path match
        if (lowerReply.contains(lowerPath)) {
            return true;
        }

        // 2. Subpath match
        String[] segments = filePath.split("/");
        String fileName = segments[segments.length - 1];
        String lowerFileName = fileName.toLowerCase(java.util.Locale.ROOT);

        if (segments.length >= 2) {
            String subpath2 = (segments[segments.length - 2] + "/" + segments[segments.length - 1]).toLowerCase(java.util.Locale.ROOT);
            if (lowerReply.contains(subpath2)) {
                return true;
            }
        }
        if (segments.length >= 3) {
            String subpath3 = (segments[segments.length - 3] + "/" + segments[segments.length - 2] + "/" + segments[segments.length - 1]).toLowerCase(java.util.Locale.ROOT);
            if (lowerReply.contains(subpath3)) {
                return true;
            }
        }

        // 3. Dynamic route segment match (e.g. [shareToken] or shareToken)
        for (String segment : segments) {
            if (segment.startsWith("[") && segment.endsWith("]")) {
                String inner = segment.substring(1, segment.length() - 1);
                if (lowerReply.contains(segment.toLowerCase(java.util.Locale.ROOT)) || lowerReply.contains(inner.toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }

        // 4. Filename match for non-generic files
        if (!GENERIC_FILE_NAMES.contains(lowerFileName)) {
            if (lowerReply.contains(lowerFileName)) {
                return true;
            }

            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                String baseName = fileName.substring(0, dotIdx);
                if (baseName.length() >= 4 && !NON_DISTINCTIVE_BASE_NAMES.contains(baseName.toLowerCase(java.util.Locale.ROOT))) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(baseName) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
                    if (p.matcher(reply).find()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
