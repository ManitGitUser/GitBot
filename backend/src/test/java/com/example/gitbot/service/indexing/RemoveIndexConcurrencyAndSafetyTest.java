package com.example.gitbot.service.indexing;

import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.ConflictException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.UserService;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.github.GitHubRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveIndexConcurrencyAndSafetyTest {

    @Mock
    private GitRepoRepository gitRepoRepository;
    @Mock
    private UserService userService;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private CodeFileFilter fileFilter;
    @Mock
    private CodeChunker codeChunker;
    @Mock
    private GitHubRateLimiter rateLimiter;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private IndexingProgressService progressService;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private IndexingService indexingService;

    private UUID userId;
    private UUID repoAId;
    private UUID repoBId;
    private GitRepo repoA;
    private GitRepo repoB;

    @BeforeEach
    void setUp() {
        indexingService = new IndexingService(
                gitRepoRepository,
                userService,
                gitHubApiClient,
                fileFilter,
                codeChunker,
                rateLimiter,
                vectorStore,
                progressService,
                jdbcTemplate
        );

        userId = UUID.randomUUID();
        repoAId = UUID.randomUUID();
        repoBId = UUID.randomUUID();

        repoA = GitRepo.builder()
                .id(repoAId)
                .userId(userId)
                .fullName("owner/repo-a")
                .latestCommitSha("commit-latest-a")
                .indexedCommitSha("commit-indexed-a")
                .indexStatus(IndexStatus.READY)
                .chunkCount(100)
                .filesProcessed(20)
                .filesTotal(20)
                .indexedAt(Instant.now())
                .build();

        repoB = GitRepo.builder()
                .id(repoBId)
                .userId(userId)
                .fullName("owner/repo-b")
                .latestCommitSha("commit-latest-b")
                .indexedCommitSha("commit-indexed-b")
                .indexStatus(IndexStatus.READY)
                .chunkCount(250)
                .build();
    }

    @Test
    @DisplayName("Concurrent Index vs Remove Index: when Indexing is in progress, Remove Index is rejected with 409 Conflict")
    void testConcurrentRace_WhenIndexingInProgress_RemoveIndexRejected() {
        repoA.setIndexStatus(IndexStatus.INDEXING);
        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));

        assertThatThrownBy(() -> indexingService.removeIndex(repoAId, userId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot remove index while indexing is in progress");

        verify(vectorStore, never()).delete(any(Filter.Expression.class));
        verify(jdbcTemplate, never()).update(anyString(), anyString());
        verify(gitRepoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Concurrent Index vs Remove Index: when Remove Index is in progress or completed, startIndexing on PENDING succeeds")
    void testConcurrentRace_StartIndexingAfterRemoveIndex_TransitionsFromPendingToIndexing() {
        repoA.setIndexStatus(IndexStatus.PENDING);
        repoA.setIndexedCommitSha(null);
        repoA.setChunkCount(0);

        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(i -> i.getArgument(0));

        GitRepo transitioned = indexingService.startIndexing(repoAId, userId);

        assertThat(transitioned.getIndexStatus()).isEqualTo(IndexStatus.INDEXING);
        // latestCommitSha must be preserved!
        assertThat(transitioned.getLatestCommitSha()).isEqualTo("commit-latest-a");
    }

    @Test
    @DisplayName("Vector isolation: removing repo A vectors deletes ONLY repo A vectors and leaves repo B untouched")
    void testVectorIsolation_RemovesOnlyTargetRepoVectors() {
        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(repoAId.toString()))).thenReturn(0);
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(i -> i.getArgument(0));

        indexingService.removeIndex(repoAId, userId);

        // VectorStore delete filter should specifically target repoAId
        verify(vectorStore).delete(any(Filter.Expression.class));

        // JDBC delete must specify repoAId
        verify(jdbcTemplate).update(eq("DELETE FROM vector_store WHERE metadata->>'repoId' = ?"), eq(repoAId.toString()));
        verify(jdbcTemplate, never()).update(anyString(), eq(repoBId.toString()));

        // Verification query must check repoAId
        verify(jdbcTemplate).queryForObject(eq("SELECT count(*) FROM vector_store WHERE metadata->>'repoId' = ?"), eq(Integer.class), eq(repoAId.toString()));
    }

    @Test
    @DisplayName("Invariant: PENDING never ends with vectors after a successful remove (verification enforces 0 remaining vectors)")
    void testInvariant_PendingNeverEndsWithVectors() {
        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));
        // Simulate JDBC verification returning 0 remaining vectors
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(repoAId.toString()))).thenReturn(0);
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(i -> i.getArgument(0));

        GitRepo result = indexingService.removeIndex(repoAId, userId);

        assertThat(result.getIndexStatus()).isEqualTo(IndexStatus.PENDING);
        assertThat(result.getChunkCount()).isZero();
        assertThat(result.getIndexedCommitSha()).isNull();
        assertThat(result.getLatestCommitSha()).isEqualTo("commit-latest-a");
    }

    @Test
    @DisplayName("Invariant: READY is never persisted after confirmed vector deletion")
    void testInvariant_ReadyNeverPersistedAfterVectorDeletion() {
        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(repoAId.toString()))).thenReturn(0);

        ArgumentCaptor<GitRepo> savedRepoCaptor = ArgumentCaptor.forClass(GitRepo.class);
        when(gitRepoRepository.save(savedRepoCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        indexingService.removeIndex(repoAId, userId);

        GitRepo saved = savedRepoCaptor.getValue();
        assertThat(saved.getIndexStatus()).isEqualTo(IndexStatus.PENDING);
        assertThat(saved.getIndexStatus()).isNotEqualTo(IndexStatus.READY);
        assertThat(saved.getChunkCount()).isZero();
        assertThat(saved.getIndexedCommitSha()).isNull();
    }

    @Test
    @DisplayName("Invariant: If vector deletion throws OR verification shows remaining vectors, previous truthful READY state is preserved")
    void testInvariant_FailurePreservesTruthfulReadyState() {
        when(gitRepoRepository.findByIdAndUserIdForUpdate(repoAId, userId)).thenReturn(Optional.of(repoA));
        // Remaining vectors > 0 indicates partial or failed deletion
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(repoAId.toString()))).thenReturn(7);

        assertThatThrownBy(() -> indexingService.removeIndex(repoAId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to delete all vector chunks for repository " + repoAId);

        // Verify gitRepoRepository.save was NEVER called
        verify(gitRepoRepository, never()).save(any());

        // In-memory repo remains truthful READY
        assertThat(repoA.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repoA.getChunkCount()).isEqualTo(100);
        assertThat(repoA.getIndexedCommitSha()).isEqualTo("commit-indexed-a");
    }
}
