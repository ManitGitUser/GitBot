package com.example.gitbot.service.ai;

import com.example.gitbot.dto.CitationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationMapperTest {

    private CitationMapper citationMapper;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        citationMapper = new CitationMapper(jsonMapper);
    }

    @Test
    void testCompleteMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filePath", "src/main/App.java");
        metadata.put("startLine", 10);
        metadata.put("endLine", 35);
        metadata.put("language", "java");
        metadata.put("repoFullName", "octocat/Hello-World");

        Document doc = new Document("some code", metadata);
        CitationDto dto = citationMapper.fromDocument(doc);

        assertThat(dto.filePath()).isEqualTo("src/main/App.java");
        assertThat(dto.startLine()).isEqualTo(10);
        assertThat(dto.endLine()).isEqualTo(35);
        assertThat(dto.language()).isEqualTo("java");
        assertThat(dto.repoFullName()).isEqualTo("octocat/Hello-World");
    }

    @Test
    void testStartLineEndLineParsedFromStrings() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filePath", "index.ts");
        metadata.put("startLine", "42");
        metadata.put("endLine", "84");
        metadata.put("language", "typescript");
        metadata.put("repoFullName", "facebook/react");

        Document doc = new Document("some code", metadata);
        CitationDto dto = citationMapper.fromDocument(doc);

        assertThat(dto.filePath()).isEqualTo("index.ts");
        assertThat(dto.startLine()).isEqualTo(42);
        assertThat(dto.endLine()).isEqualTo(84);
        assertThat(dto.language()).isEqualTo("typescript");
        assertThat(dto.repoFullName()).isEqualTo("facebook/react");
    }

    @Test
    void testMissingLineMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filePath", "README.md");
        metadata.put("language", "markdown");

        Document doc = new Document("# README", metadata);
        CitationDto dto = citationMapper.fromDocument(doc);

        assertThat(dto.filePath()).isEqualTo("README.md");
        assertThat(dto.startLine()).isNull();
        assertThat(dto.endLine()).isNull();
        assertThat(dto.language()).isEqualTo("markdown");
        assertThat(dto.repoFullName()).isNull();
    }

    @Test
    void testMalformedLineMetadataDoesNotCrash() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filePath", "bad.py");
        metadata.put("startLine", "not_a_number");
        metadata.put("endLine", "also_invalid");

        Document doc = new Document("code", metadata);
        CitationDto dto = citationMapper.fromDocument(doc);

        assertThat(dto.filePath()).isEqualTo("bad.py");
        assertThat(dto.startLine()).isNull();
        assertThat(dto.endLine()).isNull();
    }

    @Test
    void testRepoFullNameExtraction() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filePath", "main.go");
        metadata.put("repoFullName", "golang/go");

        Document doc = new Document("package main", metadata);
        CitationDto dto = citationMapper.fromDocument(doc);

        assertThat(dto.repoFullName()).isEqualTo("golang/go");
    }

    @Test
    void testJsonSerializationAndDeserialization() {
        List<CitationDto> original = List.of(
                new CitationDto("src/A.java", 1, 20, "java", "org/repo-a"),
                new CitationDto("src/B.py", null, null, "python", "org/repo-b")
        );

        String json = citationMapper.toJson(original);
        assertThat(json).isNotBlank();
        assertThat(json).contains("src/A.java");
        assertThat(json).contains("org/repo-a");
        assertThat(json).contains("1");
        assertThat(json).contains("20");

        List<CitationDto> parsed = citationMapper.fromJson(json);
        assertThat(parsed).hasSize(2);

        CitationDto c1 = parsed.get(0);
        assertThat(c1.filePath()).isEqualTo("src/A.java");
        assertThat(c1.startLine()).isEqualTo(1);
        assertThat(c1.endLine()).isEqualTo(20);
        assertThat(c1.language()).isEqualTo("java");
        assertThat(c1.repoFullName()).isEqualTo("org/repo-a");

        CitationDto c2 = parsed.get(1);
        assertThat(c2.filePath()).isEqualTo("src/B.py");
        assertThat(c2.startLine()).isNull();
        assertThat(c2.endLine()).isNull();
        assertThat(c2.language()).isEqualTo("python");
        assertThat(c2.repoFullName()).isEqualTo("org/repo-b");
    }

    @Test
    void testEmptyAndNullJsonHandling() {
        assertThat(citationMapper.fromJson(null)).isEmpty();
        assertThat(citationMapper.fromJson("")).isEmpty();
        assertThat(citationMapper.fromJson("   ")).isEmpty();
        assertThat(citationMapper.fromJson("invalid-json")).isEmpty();
    }

    @Test
    void irrelevantQuestion_returnsZeroCitations() {
        List<CitationDto> candidates = List.of(
                new CitationDto("client/components/ui/button-group.tsx", 1, 30, "typescript", "org/repo"),
                new CitationDto("client/components/ui/calendar.tsx", 1, 50, "typescript", "org/repo"),
                new CitationDto("client/components/ui/sidebar.tsx", 1, 40, "typescript", "org/repo")
        );

        String reply1 = "The provided context does not contain any information or relevant source code related to \"bund\". Please clarify your question or provide more context so I can assist you effectively.";
        assertThat(citationMapper.filterSupportingCitations(reply1, candidates)).isEmpty();

        String reply2 = "The provided context does not mention anything about this topic.";
        assertThat(citationMapper.filterSupportingCitations(reply2, candidates)).isEmpty();

        String reply3 = "Based on the provided context, there is no information available regarding the requested feature.";
        assertThat(citationMapper.filterSupportingCitations(reply3, candidates)).isEmpty();

        String reply4 = "The provided code context does not contain relevant source code for this query.";
        assertThat(citationMapper.filterSupportingCitations(reply4, candidates)).isEmpty();
    }

    @Test
    void casualConversation_returnsZeroCitations() {
        List<CitationDto> candidates = List.of(
                new CitationDto("client/components/ui/button-group.tsx", 1, 30, "typescript", "org/repo"),
                new CitationDto("client/components/ui/calendar.tsx", 1, 50, "typescript", "org/repo")
        );

        String greeting1 = "Hello! I am GitBot, your technical assistant for this repository. How can I help you with the codebase today?";
        assertThat(citationMapper.filterSupportingCitations(greeting1, candidates)).isEmpty();

        String greeting2 = "Hi! How can I assist you with the repository today?";
        assertThat(citationMapper.filterSupportingCitations(greeting2, candidates)).isEmpty();

        String greeting3 = "I'm doing well, thank you! Feel free to ask any questions about the code.";
        assertThat(citationMapper.filterSupportingCitations(greeting3, candidates)).isEmpty();

        String pleasantry = "You're welcome! Let me know if you need anything else.";
        assertThat(citationMapper.filterSupportingCitations(pleasantry, candidates)).isEmpty();
    }

    @Test
    void repositoryGroundedAnswer_returnsOnlySupportingCitations() {
        CitationDto securityConfig = new CitationDto("backend/src/main/java/com/example/gitbot/config/SecurityConfig.java", 1, 50, "java", "org/repo");
        CitationDto userService = new CitationDto("backend/src/main/java/com/example/gitbot/service/UserService.java", 1, 60, "java", "org/repo");
        CitationDto buttonGroup = new CitationDto("client/components/ui/button-group.tsx", 1, 30, "typescript", "org/repo");

        List<CitationDto> candidates = List.of(securityConfig, userService, buttonGroup);

        // Reply references SecurityConfig.java
        String reply = "GitHub OAuth is configured in `backend/src/main/java/com/example/gitbot/config/SecurityConfig.java` in the `securityFilterChain` method.";
        List<CitationDto> supporting = citationMapper.filterSupportingCitations(reply, candidates);

        assertThat(supporting).hasSize(1);
        assertThat(supporting.get(0).filePath()).isEqualTo("backend/src/main/java/com/example/gitbot/config/SecurityConfig.java");

        // Reply references both SecurityConfig and UserService
        String multiReply = "Authentication is set up in `SecurityConfig.java`, while user details and GitHub tokens are handled in `UserService.java`.";
        List<CitationDto> multiSupporting = citationMapper.filterSupportingCitations(multiReply, candidates);

        assertThat(multiSupporting).hasSize(2);
        assertThat(multiSupporting).extracting(CitationDto::filePath).containsExactlyInAnyOrder(
                "backend/src/main/java/com/example/gitbot/config/SecurityConfig.java",
                "backend/src/main/java/com/example/gitbot/service/UserService.java"
        );
    }

    @Test
    void dynamicRouteCitation_returnsMatchingCitation() {
        CitationDto sharePage = new CitationDto("client/app/share/[shareToken]/page.tsx", 1, 100, "typescript", "org/repo");
        CitationDto buttonGroup = new CitationDto("client/components/ui/button-group.tsx", 1, 30, "typescript", "org/repo");

        List<CitationDto> candidates = List.of(sharePage, buttonGroup);

        // Reference using full path
        String reply1 = "The public share page is defined in `client/app/share/[shareToken]/page.tsx`.";
        List<CitationDto> res1 = citationMapper.filterSupportingCitations(reply1, candidates);
        assertThat(res1).hasSize(1);
        assertThat(res1.get(0).filePath()).isEqualTo("client/app/share/[shareToken]/page.tsx");

        // Reference using dynamic segment [shareToken]
        String reply2 = "You can view shared conversations at the `/share/[shareToken]` route.";
        List<CitationDto> res2 = citationMapper.filterSupportingCitations(reply2, candidates);
        assertThat(res2).hasSize(1);
        assertThat(res2.get(0).filePath()).isEqualTo("client/app/share/[shareToken]/page.tsx");
    }
}
