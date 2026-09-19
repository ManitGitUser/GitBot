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
}
