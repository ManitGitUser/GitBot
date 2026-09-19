package com.example.gitbot.service.ai;

import com.example.gitbot.dto.RetrievedContextDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodeContextRetrieverTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CitationMapper citationMapper;
    private JsonMapper jsonMapper;
    private UUID repoA;
    private UUID repoB;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        citationMapper = new CitationMapper(jsonMapper);
        repoA = UUID.randomUUID();
        repoB = UUID.randomUUID();
    }

    private Document createDoc(UUID repoId, String filePath, int chunkIndex, int startLine, int endLine, String lang, String text) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("repoId", repoId.toString());
        meta.put("repoFullName", "owner/repo");
        meta.put("filePath", filePath);
        meta.put("chunkIndex", chunkIndex);
        meta.put("startLine", startLine);
        meta.put("endLine", endLine);
        meta.put("language", lang);
        return new Document(text, meta);
    }

    @Test
    void testRepositoryIsolation_RepoACannotReturnRepoBChunks() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0
        );

        // Vector store mistakenly returns a mix of Repo A and Repo B documents
        Document docA = createDoc(repoA, "src/A.java", 0, 1, 10, "java", "code A");
        Document docB = createDoc(repoB, "src/B.java", 0, 1, 10, "java", "code B from another repo!");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docA, docB));

        RetrievedContextDto result = retriever.retrieve(repoA, "How does service A work?");

        // Verify SearchRequest filter explicitly specified repoA
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString()).contains(repoA.toString());

        // Verify defensive filter stripped repoB doc
        assertThat(result.contextText()).contains("src/A.java");
        assertThat(result.contextText()).doesNotContain("src/B.java");
        assertThat(result.contextText()).doesNotContain("code B from another repo!");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filePath()).isEqualTo("src/A.java");
    }

    @Test
    void testTopKConfiguration_PassedToSearchRequest() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 12, 12000, false, 0
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        retriever.retrieve(repoA, "some search");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getTopK()).isEqualTo(12);
    }

    @Test
    void testDeduplication_RemovesDuplicateChunksByStableKey() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0
        );

        // Two documents with identical repoId, filePath, and chunkIndex
        Document doc1 = createDoc(repoA, "src/App.java", 2, 20, 35, "java", "public void start() { 1 }");
        Document doc2 = createDoc(repoA, "src/App.java", 2, 20, 35, "java", "public void start() { 1 }");
        Document doc3 = createDoc(repoA, "src/App.java", 3, 36, 50, "java", "public void stop() { 2 }");

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2, doc3));

        RetrievedContextDto result = retriever.retrieve(repoA, "start method");

        assertThat(result.citations()).hasSize(2);
        assertThat(result.contextText()).contains("lines: 20-35");
        assertThat(result.contextText()).contains("lines: 36-50");
        // Count occurrences of start() - should only be 1
        int count = result.contextText().split("public void start\\(\\)", -1).length - 1;
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testHighestRankGuarantee_NeverDropsTopChunkEvenIfOversized() {
        // maxContextChars is 400
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 400, false, 0
        );

        // Chunk 1 has 1000 characters of code - much larger than 400
        String hugeCode = "int x = 1;\n".repeat(100);
        Document docHuge = createDoc(repoA, "src/Huge.java", 0, 1, 100, "java", hugeCode);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docHuge));

        RetrievedContextDto result = retriever.retrieve(repoA, "find huge");

        // Top chunk is NOT dropped
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filePath()).isEqualTo("src/Huge.java");
        assertThat(result.contextText()).contains("src/Huge.java");
        assertThat(result.contextText()).contains("[truncated to fit context budget]");
        // Context total length does not exceed budget
        assertThat(result.contextText().length()).isLessThanOrEqualTo(400);
    }

    @Test
    void testContextBudget_DropsSubsequentChunksWhenLimitReached() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 600, false, 0
        );

        Document doc1 = createDoc(repoA, "src/First.java", 0, 1, 10, "java", "First small code");
        Document doc2 = createDoc(repoA, "src/Second.java", 0, 1, 10, "java", "Second small code");
        Document doc3 = createDoc(repoA, "src/Third.java", 0, 1, 10, "java", "Third code ".repeat(40)); // ~440 chars

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc1, doc2, doc3));

        RetrievedContextDto result = retriever.retrieve(repoA, "query");

        // Primary results 1 and 2 fit, result 3 would exceed 600 chars and is omitted
        assertThat(result.contextText()).contains("src/First.java");
        assertThat(result.contextText()).contains("src/Second.java");
        assertThat(result.contextText()).doesNotContain("src/Third.java");
        assertThat(result.contextText().length()).isLessThanOrEqualTo(600);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testNeighboringChunks_AppendsAdjacentChunksWhenSpaceRemains() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, jdbcTemplate, jsonMapper, 10, 12000, true, 2
        );

        Document primaryDoc = createDoc(repoA, "src/Service.java", 5, 50, 70, "java", "void doWork() {}");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(primaryDoc));

        // Mock SQL response for neighbor chunkIndex 4 and 6
        Document neighborDoc = createDoc(repoA, "src/Service.java", 6, 71, 90, "java", "void doMoreWork() {}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(repoA.toString()), eq("src/Service.java"), eq(4)))
                .thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(repoA.toString()), eq("src/Service.java"), eq(6)))
                .thenReturn(List.of(neighborDoc));

        RetrievedContextDto result = retriever.retrieve(repoA, "doWork");

        assertThat(result.citations()).hasSize(2);
        assertThat(result.contextText()).contains("lines: 50-70");
        assertThat(result.contextText()).contains("lines: 71-90");
        // Primary result is ordered before neighbor
        int primaryPos = result.contextText().indexOf("lines: 50-70");
        int neighborPos = result.contextText().indexOf("lines: 71-90");
        assertThat(primaryPos).isLessThan(neighborPos);
    }

    @Test
    void testMissingMetadata_HandledDefensivelyWithoutCrashing() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0
        );

        // Document with empty metadata
        Document docNoMeta = new Document("some code snippet", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(docNoMeta));

        RetrievedContextDto result = retriever.retrieve(repoA, "snippet");

        assertThat(result.contextText()).contains("<source>");
        assertThat(result.contextText()).contains("file: unknown");
        assertThat(result.contextText()).contains("lines: unknown");
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void testEmptyRetrieval_ReturnsCleanNoMatchesMessage() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RetrievedContextDto result = retriever.retrieve(repoA, "nonexistent concept");

        assertThat(result.citations()).isEmpty();
        assertThat(result.contextText()).contains("no matching code chunks found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testLightweightLexicalFallback_TriggeredWhenVectorSearchIsEmpty() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, jdbcTemplate, jsonMapper, 10, 12000, false, 0
        );

        // Vector search returns 0 results for exact class name query
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        Document lexicalDoc = createDoc(repoA, "src/SecurityConfig.java", 0, 1, 25, "java", "class SecurityConfig {}");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(repoA.toString()), contains("SecurityConfig"), anyInt()))
                .thenReturn(List.of(lexicalDoc));

        RetrievedContextDto result = retriever.retrieve(repoA, "Where is SecurityConfig?");

        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).filePath()).isEqualTo("src/SecurityConfig.java");
        assertThat(result.contextText()).contains("src/SecurityConfig.java");
    }

    @Test
    void testCitationMetadataSurvivesRetrieval() {
        CodeContextRetriever retriever = new CodeContextRetriever(
                vectorStore, citationMapper, null, jsonMapper, 10, 12000, false, 0
        );

        Document doc = createDoc(repoA, "backend/src/App.java", 1, 15, 45, "java", "public class App {}");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        RetrievedContextDto result = retriever.retrieve(repoA, "find app");

        assertThat(result.citations()).hasSize(1);
        var citation = result.citations().get(0);
        assertThat(citation.filePath()).isEqualTo("backend/src/App.java");
        assertThat(citation.startLine()).isEqualTo(15);
        assertThat(citation.endLine()).isEqualTo(45);
        assertThat(citation.language()).isEqualTo("java");
        assertThat(citation.repoFullName()).isEqualTo("owner/repo");
    }
}
