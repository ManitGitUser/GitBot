package com.example.gitbot.service.indexing;

import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.UserService;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.github.GitHubRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private GitRepoRepository gitRepoRepository;
    @Mock
    private UserService userService;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private CodeFileFilter codeFileFilter;
    @Mock
    private CodeChunker codeChunker;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private IndexingProgressService progressService;
    @Mock
    private GitHubRateLimiter rateLimiter;

    @InjectMocks
    private IndexingService indexingService;

    private UUID repoId;
    private UUID userId;
    private GitRepo repo;
    private User user;

    @BeforeEach
    void setUp() {
        repoId = UUID.randomUUID();
        userId = UUID.randomUUID();

        repo = GitRepo.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("Hello-World")
                .fullName("octocat/Hello-World")
                .defaultBranch("main")
                .indexStatus(IndexStatus.PENDING)
                .build();

        user = User.builder()
                .id(userId)
                .githubUsername("octocat")
                .accessToken("encrypted_token")
                .build();
    }

    private Map<String, Object> createTree(List<String> paths) {
        List<Map<String, Object>> treeList = new ArrayList<>();
        for (String p : paths) {
            treeList.add(Map.of("path", p, "type", "blob"));
        }
        return Map.of("tree", treeList);
    }

    @Test
    void testSuccessfulRun_CleansUpOldVectorsAndMarksReady() {
        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        List<String> files = List.of("src/Main.java", "src/Util.java");
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn(createTree(files));
        when(codeFileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);

        when(gitHubApiClient.getFileContent(eq("raw_token"), eq("octocat"), eq("Hello-World"), eq("src/Main.java"), any()))
                .thenReturn("public class Main {}");
        when(gitHubApiClient.getFileContent(eq("raw_token"), eq("octocat"), eq("Hello-World"), eq("src/Util.java"), any()))
                .thenReturn("public class Util {}");

        Document doc1 = new Document("Main chunk", Map.of("chunkIndex", 0));
        Document doc2 = new Document("Util chunk", Map.of("chunkIndex", 0));
        when(codeChunker.chunkFile(eq(repoId.toString()), eq("octocat/Hello-World"), eq("src/Main.java"), anyString(), anyString()))
                .thenReturn(List.of(doc1));
        when(codeChunker.chunkFile(eq(repoId.toString()), eq("octocat/Hello-World"), eq("src/Util.java"), anyString(), anyString()))
                .thenReturn(List.of(doc2));

        indexingService.indexAsync(repoId, userId);

        // Chunks were added to vectorStore
        verify(vectorStore, atLeastOnce()).add(anyList());

        // Old runs were purged (vectorStore.delete called with filter excluding current runId)
        verify(vectorStore, atLeastOnce()).delete(any(Filter.Expression.class));

        // Progress marked ready with consistent counts
        verify(progressService).markReady(eq(repoId), eq(2), eq(2), eq(2), eq("octocat/Hello-World"), any());
        verify(progressService, never()).markFailed(any(), any());
    }

    @Test
    void testFailure_CleansUpOnlyCurrentRunAndMarksFailed() {
        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        List<String> files = List.of("src/Main.java");
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn(createTree(files));
        when(codeFileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);

        // GitHub API throws 401 Unauthorized fatal exception
        when(gitHubApiClient.getFileContent(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));

        indexingService.indexAsync(repoId, userId);

        // Failed status is recorded
        verify(progressService).markFailed(eq(repoId), anyString());
        verify(progressService, never()).markReady(any(), anyInt(), anyInt(), anyInt(), any(), any());

        // Vector store delete is called to clean up the current run
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    void testIndividualFileSkippedGracefully() {
        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        List<String> files = List.of("src/Bad.java", "src/Good.java");
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn(createTree(files));
        when(codeFileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);

        // First file throws non-fatal 404
        when(gitHubApiClient.getFileContent(eq("raw_token"), eq("octocat"), eq("Hello-World"), eq("src/Bad.java"), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null));
        // Second file succeeds
        when(gitHubApiClient.getFileContent(eq("raw_token"), eq("octocat"), eq("Hello-World"), eq("src/Good.java"), any()))
                .thenReturn("public class Good {}");

        Document doc = new Document("Good chunk", Map.of("chunkIndex", 0));
        when(codeChunker.chunkFile(eq(repoId.toString()), eq("octocat/Hello-World"), eq("src/Good.java"), anyString(), anyString()))
                .thenReturn(List.of(doc));

        indexingService.indexAsync(repoId, userId);

        // Vector store added only good file chunks
        verify(vectorStore).add(anyList());

        // Successfully marked ready because 1 file succeeded
        verify(progressService).markReady(eq(repoId), eq(2), eq(2), eq(1), eq("octocat/Hello-World"), any());
        verify(progressService, never()).markFailed(any(), any());
    }

    @Test
    void testProgressConsistency_ProcessedNeverExceedsTotal() {
        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        List<String> files = List.of("A.java", "B.java", "C.java");
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn(createTree(files));
        when(codeFileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);

        when(gitHubApiClient.getFileContent(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("content");
        when(codeChunker.chunkFile(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new Document("c", Map.of())));

        indexingService.indexAsync(repoId, userId);

        ArgumentCaptor<Integer> totalCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> processedCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(progressService, atLeastOnce()).updateProgress(
                eq(repoId),
                totalCaptor.capture(),
                processedCaptor.capture(),
                anyInt(),
                eq(IndexStatus.INDEXING),
                isNull()
        );

        List<Integer> totals = totalCaptor.getAllValues();
        List<Integer> processedList = processedCaptor.getAllValues();

        for (int i = 0; i < totals.size(); i++) {
            assertThat(processedList.get(i)).isLessThanOrEqualTo(totals.get(i));
            assertThat(processedList.get(i)).isGreaterThanOrEqualTo(0);
        }

        verify(progressService).markReady(eq(repoId), eq(3), eq(3), eq(3), eq("octocat/Hello-World"), any());
    }

    @Test
    void testDanglingIndexingJobsResetOnStartup() {
        GitRepo dangling1 = GitRepo.builder()
                .id(UUID.randomUUID())
                .fullName("user/repo1")
                .indexStatus(IndexStatus.INDEXING)
                .build();
        GitRepo dangling2 = GitRepo.builder()
                .id(UUID.randomUUID())
                .fullName("user/repo2")
                .indexStatus(IndexStatus.INDEXING)
                .build();

        when(gitRepoRepository.findByIndexStatus(IndexStatus.INDEXING))
                .thenReturn(List.of(dangling1, dangling2));

        indexingService.resetDanglingIndexingJobs();

        // Clears vectors for both dangling repos
        verify(vectorStore, times(2)).delete(any(Filter.Expression.class));

        // Marks both failed with restart message
        verify(progressService).markFailed(eq(dangling1.getId()), contains("interrupted by server restart"));
        verify(progressService).markFailed(eq(dangling2.getId()), contains("interrupted by server restart"));
    }
}
