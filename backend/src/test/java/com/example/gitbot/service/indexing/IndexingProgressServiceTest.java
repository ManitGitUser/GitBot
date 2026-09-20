package com.example.gitbot.service.indexing;

import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.repository.GitRepoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingProgressServiceTest {

    @Mock
    private GitRepoRepository gitRepoRepository;

    @InjectMocks
    private IndexingProgressService progressService;

    @Test
    void updateProgress_updatesCountsAndStatus() {
        UUID repoId = UUID.randomUUID();
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .filesTotal(0)
                .filesProcessed(0)
                .chunkCount(0)
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        progressService.updateProgress(repoId, 50, 10, 25, IndexStatus.INDEXING, null);

        assertThat(repo.getFilesTotal()).isEqualTo(50);
        assertThat(repo.getFilesProcessed()).isEqualTo(10);
        assertThat(repo.getChunkCount()).isEqualTo(25);
        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.INDEXING);
        assertThat(repo.getErrorMessage()).isNull();

        verify(gitRepoRepository).save(repo);
    }

    @Test
    void markReady_setsReadyStatusAndIndexedAt() {
        UUID repoId = UUID.randomUUID();
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        progressService.markReady(repoId, 100, 100, 300, "owner/repo", null);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getFilesTotal()).isEqualTo(100);
        assertThat(repo.getFilesProcessed()).isEqualTo(100);
        assertThat(repo.getChunkCount()).isEqualTo(300);
        assertThat(repo.getIndexedAt()).isNotNull();
        assertThat(repo.getErrorMessage()).isNull();

        verify(gitRepoRepository).save(repo);
    }

    @Test
    void markFailed_truncatesLongErrorMessage() {
        UUID repoId = UUID.randomUUID();
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        String longError = "x".repeat(3000);
        progressService.markFailed(repoId, longError);

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.FAILED);
        assertThat(repo.getErrorMessage()).hasSize(2000);

        verify(gitRepoRepository).save(repo);
    }

    @Test
    void markReady_preservesNewerLatestCommitSha_whenIndexingFinishes() {
        UUID repoId = UUID.randomUUID();
        // State: latestCommitSha = D (discovered while indexing C), indexedCommitSha = A
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .indexedCommitSha("sha-A")
                .latestCommitSha("sha-D")
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        // Indexing C finishes successfully
        progressService.markReady(repoId, 10, 10, 20, "owner/repo", "sha-C");

        // After successful indexing: latestCommitSha == D, indexedCommitSha == C
        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getIndexedCommitSha()).isEqualTo("sha-C");
        assertThat(repo.getLatestCommitSha()).isEqualTo("sha-D");

        verify(gitRepoRepository).save(repo);
    }

    @Test
    void markReady_updatesIndexedCommitSha_andPreservesMatchingLatestCommitSha() {
        UUID repoId = UUID.randomUUID();
        // State: latestCommitSha = C, indexedCommitSha = A
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .indexedCommitSha("sha-A")
                .latestCommitSha("sha-C")
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        // Indexing C finishes successfully
        progressService.markReady(repoId, 10, 10, 20, "owner/repo", "sha-C");

        // After successful indexing: latestCommitSha == C, indexedCommitSha == C
        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getIndexedCommitSha()).isEqualTo("sha-C");
        assertThat(repo.getLatestCommitSha()).isEqualTo("sha-C");

        verify(gitRepoRepository).save(repo);
    }

    @Test
    void markReady_initializesLatestCommitSha_whenNull() {
        UUID repoId = UUID.randomUUID();
        GitRepo repo = GitRepo.builder()
                .id(repoId)
                .indexStatus(IndexStatus.INDEXING)
                .indexedCommitSha(null)
                .latestCommitSha(null)
                .build();

        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));
        when(gitRepoRepository.save(any(GitRepo.class))).thenAnswer(inv -> inv.getArgument(0));

        progressService.markReady(repoId, 10, 10, 20, "owner/repo", "sha-C");

        assertThat(repo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(repo.getIndexedCommitSha()).isEqualTo("sha-C");
        assertThat(repo.getLatestCommitSha()).isEqualTo("sha-C");

        verify(gitRepoRepository).save(repo);
    }
}
