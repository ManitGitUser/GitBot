package com.example.gitbot.service;

import com.example.gitbot.dto.SyncRepoResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.github.GitHubRateLimiter;
import com.example.gitbot.service.indexing.CodeChunker;
import com.example.gitbot.service.indexing.CodeFileFilter;
import com.example.gitbot.service.indexing.IndexingProgressService;
import com.example.gitbot.service.indexing.IndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncRepoTest {

    @Mock
    private GitRepoRepository gitRepoRepository;
    @Mock
    private UserService userService;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private IndexingService indexingService;

    // For indexing pipeline tests
    @Mock
    private CodeFileFilter codeFileFilter;
    @Mock
    private CodeChunker codeChunker;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private GitHubRateLimiter gitHubRateLimiter;
    @Mock
    private IndexingProgressService progressService;

    @InjectMocks
    private GitRepoService gitRepoService;

    private UUID repoId;
    private UUID userId;
    private UUID nonOwnerId;
    private GitRepo repo;
    private User user;

    @BeforeEach
    void setUp() {
        repoId = UUID.randomUUID();
        userId = UUID.randomUUID();
        nonOwnerId = UUID.randomUUID();

        repo = GitRepo.builder()
                .id(repoId)
                .userId(userId)
                .owner("octocat")
                .name("Hello-World")
                .fullName("octocat/Hello-World")
                .defaultBranch("main")
                .indexStatus(IndexStatus.READY)
                .indexedCommitSha("sha-aaa")
                .build();

        user = User.builder()
                .id(userId)
                .githubUsername("octocat")
                .accessToken("enc_token")
                .build();
    }

    @Test
    @DisplayName("Test 1 — Already current: indexed SHA == GitHub SHA → no indexing")
    void test1_alreadyCurrent_noIndexing() {
        when(gitRepoRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-aaa");

        SyncRepoResponse response = gitRepoService.syncRepo(repoId, userId);

        assertThat(response.reindexTriggered()).isFalse();
        assertThat(response.message()).contains("already up to date");
        assertThat(response.currentCommitSha()).isEqualTo("sha-aaa");
        assertThat(response.indexedCommitSha()).isEqualTo("sha-aaa");

        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 2 — Stale: indexed SHA != GitHub SHA → latestCommitSha updated, no indexing triggered")
    void test2_stale_latestCommitShaUpdated_noIndexing() {
        when(gitRepoRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-bbb");

        SyncRepoResponse response = gitRepoService.syncRepo(repoId, userId);

        assertThat(response.reindexTriggered()).isFalse();
        assertThat(response.message()).contains("New commit available");
        assertThat(response.currentCommitSha()).isEqualTo("sha-bbb");
        assertThat(response.indexedCommitSha()).isEqualTo("sha-aaa");
        assertThat(repo.getLatestCommitSha()).isEqualTo("sha-bbb");
        assertThat(repo.getIndexedCommitSha()).isEqualTo("sha-aaa");

        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 3 — Never indexed: indexedCommitSha == null → latestCommitSha updated, no indexing triggered")
    void test3_neverIndexed_latestCommitShaUpdated_noIndexing() {
        repo.setIndexedCommitSha(null);
        repo.setIndexStatus(IndexStatus.PENDING);

        when(gitRepoRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-first");

        SyncRepoResponse response = gitRepoService.syncRepo(repoId, userId);

        assertThat(response.reindexTriggered()).isFalse();
        assertThat(response.indexedCommitSha()).isNull();
        assertThat(response.currentCommitSha()).isEqualTo("sha-first");
        assertThat(repo.getLatestCommitSha()).isEqualTo("sha-first");
        assertThat(repo.getIndexedCommitSha()).isNull();

        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 3b — Empty repository: getLatestCommitSha returns null → handled gracefully without indexing")
    void test3b_emptyRepository_handledGracefully() {
        when(gitRepoRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn(null);

        SyncRepoResponse response = gitRepoService.syncRepo(repoId, userId);

        assertThat(response.reindexTriggered()).isFalse();
        assertThat(response.message()).contains("empty or has no commits");
        assertThat(response.currentCommitSha()).isNull();
        assertThat(response.indexedCommitSha()).isEqualTo("sha-aaa");
        assertThat(repo.getLatestCommitSha()).isNull();

        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 4 — Successful sync: new indexing succeeds → indexedCommitSha updated")
    void test4_successfulSync_indexedCommitShaUpdated() {
        // Build IndexingService instance with mocks
        IndexingService realIndexingService = new IndexingService(
                gitRepoRepository,
                userService,
                gitHubApiClient,
                codeFileFilter,
                codeChunker,
                gitHubRateLimiter,
                vectorStore,
                progressService
        );

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-bbb");

        List<String> files = List.of("src/App.java");
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "sha-bbb"))
                .thenReturn(Map.of("tree", List.of(Map.of("path", "src/App.java", "type", "blob"))));
        when(codeFileFilter.isEligible(anyString(), anyLong(), anyLong())).thenReturn(true);
        when(gitHubApiClient.getFileContent("raw_token", "octocat", "Hello-World", "src/App.java", "sha-bbb"))
                .thenReturn("class App {}");
        when(codeChunker.chunkFile(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(new Document("chunk", Map.of("chunkIndex", 0))));

        realIndexingService.indexAsync(repoId, userId);

        // Verify markReady was called with new commit SHA
        verify(progressService).markReady(eq(repoId), eq(1), eq(1), eq(1), eq("octocat/Hello-World"), eq("sha-bbb"));
    }

    @Test
    @DisplayName("Test 5 — Failed sync: indexing fails → previous indexedCommitSha retained")
    void test5_failedSync_previousIndexedCommitShaRetained() {
        IndexingService realIndexingService = new IndexingService(
                gitRepoRepository,
                userService,
                gitHubApiClient,
                codeFileFilter,
                codeChunker,
                gitHubRateLimiter,
                vectorStore,
                progressService
        );

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-bbb");

        // Fatal API error occurs during indexing
        when(gitHubApiClient.getRepoTree("raw_token", "octocat", "Hello-World", "sha-bbb"))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));

        realIndexingService.indexAsync(repoId, userId);

        // markFailed is called, markReady is NOT called
        verify(progressService).markFailed(eq(repoId), anyString());
        verify(progressService, never()).markReady(any(), anyInt(), anyInt(), anyInt(), any(), any());

        // Previous SHA remains unchanged on repo entity
        assertThat(repo.getIndexedCommitSha()).isEqualTo("sha-aaa");
    }

    @Test
    @DisplayName("Test 6 — Owner: repository owner → sync allowed")
    void test6_owner_syncAllowed() {
        when(gitRepoRepository.findByIdAndUserId(repoId, userId)).thenReturn(Optional.of(repo));
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "Hello-World", "main"))
                .thenReturn("sha-aaa");

        SyncRepoResponse response = gitRepoService.syncRepo(repoId, userId);

        assertThat(response).isNotNull();
        assertThat(response.repoId()).isEqualTo(repoId);
    }

    @Test
    @DisplayName("Test 7 — Non-owner: non-owner → sync rejected")
    void test7_nonOwner_syncRejected() {
        when(gitRepoRepository.findByIdAndUserId(repoId, nonOwnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gitRepoService.syncRepo(repoId, nonOwnerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Repository not found");

        verify(gitHubApiClient, never()).getLatestCommitSha(any(), any(), any(), any());
        verify(indexingService, never()).startIndexing(any(), any());
    }
}
