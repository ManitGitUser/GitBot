package com.example.gitbot.service.ai;

import com.example.gitbot.dto.RetrievedContextDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Deterministic regression test suite containing 10 representative GitBot codebase queries.
 *
 * <p>NOTE: This is a regression suite verifying that the RAG retrieval pipeline faithfully
 * accepts codebase queries, queries vector store with repository isolation, preserves source
 * boundaries in &lt;source&gt; blocks, and produces citations matching the retrieved source files.
 * It is not an ML evaluation framework.
 */
@ExtendWith(MockitoExtension.class)
class RagEvaluationTest {

    public record RetrievalBenchmark(String question, String primaryExpectedFile, List<String> relevantFiles) {}

    @Mock
    private VectorStore vectorStore;

    private CodeContextRetriever retriever;
    private UUID testRepoId;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        CitationMapper citationMapper = new CitationMapper(jsonMapper);
        retriever = new CodeContextRetriever(vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0);
        testRepoId = UUID.randomUUID();
    }

    static Stream<RetrievalBenchmark> representativeCodebaseQueries() {
        return Stream.of(
                new RetrievalBenchmark(
                        "Where is GitHub OAuth configured?",
                        "backend/src/main/java/com/example/gitbot/config/SecurityConfig.java",
                        List.of("SecurityConfig.java", "application.yaml")
                ),
                new RetrievalBenchmark(
                        "How are GitHub access tokens stored?",
                        "backend/src/main/java/com/example/gitbot/service/UserService.java",
                        List.of("UserService.java", "TokenEncryptor.java")
                ),
                new RetrievalBenchmark(
                        "How does repository indexing work?",
                        "backend/src/main/java/com/example/gitbot/service/indexing/IndexingService.java",
                        List.of("IndexingService.java")
                ),
                new RetrievalBenchmark(
                        "How are code chunks generated?",
                        "backend/src/main/java/com/example/gitbot/service/indexing/CodeChunker.java",
                        List.of("CodeChunker.java")
                ),
                new RetrievalBenchmark(
                        "How does chat streaming work?",
                        "backend/src/main/java/com/example/gitbot/service/ai/ChatStreamHandler.java",
                        List.of("ChatStreamHandler.java", "ChatService.java")
                ),
                new RetrievalBenchmark(
                        "How does branch chat work?",
                        "backend/src/main/java/com/example/gitbot/service/ChatService.java",
                        List.of("ChatService.java")
                ),
                new RetrievalBenchmark(
                        "How are shared chats protected?",
                        "backend/src/main/java/com/example/gitbot/controller/PublicShareController.java",
                        List.of("PublicShareController.java", "SecurityConfig.java")
                ),
                new RetrievalBenchmark(
                        "How are repository ownership checks performed?",
                        "backend/src/main/java/com/example/gitbot/service/GitRepoService.java",
                        List.of("GitRepoService.java")
                ),
                new RetrievalBenchmark(
                        "Where is pgvector configured?",
                        "backend/src/main/resources/application.yaml",
                        List.of("application.yaml")
                ),
                new RetrievalBenchmark(
                        "How are citations generated?",
                        "backend/src/main/java/com/example/gitbot/service/ai/CitationMapper.java",
                        List.of("CitationMapper.java", "CodeContextRetriever.java")
                )
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("representativeCodebaseQueries")
    @DisplayName("Regression check for representative codebase queries")
    void testRepresentativeQueryRetrieval(RetrievalBenchmark benchmark) {
        // Mock a retrieved chunk from the expected target file
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("repoId", testRepoId.toString());
        metadata.put("repoFullName", "ManitGitUser/GitBot");
        metadata.put("filePath", benchmark.primaryExpectedFile());
        metadata.put("chunkIndex", 0);
        metadata.put("startLine", 1);
        metadata.put("endLine", 30);
        metadata.put("language", "java");

        Document doc = new Document("// Code implementation for " + benchmark.primaryExpectedFile(), metadata);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RetrievedContextDto result = retriever.retrieve(testRepoId, benchmark.question());

        // 1. Verify search request was properly isolated to test repository
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString()).contains(testRepoId.toString());

        // 2. Verify citation contains the expected primary file
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filePath()).isEqualTo(benchmark.primaryExpectedFile());
        assertThat(result.citations().get(0).repoFullName()).isEqualTo("ManitGitUser/GitBot");
        assertThat(result.citations().get(0).startLine()).isEqualTo(1);
        assertThat(result.citations().get(0).endLine()).isEqualTo(30);

        // 3. Verify formatted context wraps the file in <source> tags with line numbers
        assertThat(result.contextText()).contains("<source>");
        assertThat(result.contextText()).contains("file: " + benchmark.primaryExpectedFile());
        assertThat(result.contextText()).contains("lines: 1-30");
        assertThat(result.contextText()).contains("</source>");
    }
}
