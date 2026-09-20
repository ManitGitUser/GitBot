package com.example.gitbot.service;

import com.example.gitbot.dto.SyncAllReposResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.indexing.IndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncAllReposTest {

    @Mock
    private GitRepoRepository gitRepoRepository;
    @Mock
    private UserService userService;
    @Mock
    private GitHubApiClient gitHubApiClient;
    @Mock
    private IndexingService indexingService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private GitRepoService gitRepoService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .githubUsername("octocat")
                .accessToken("enc_token")
                .build();

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    @DisplayName("Test 1 — Sync All: discovers new repo, sets latestCommitSha, indexedCommitSha remains null, never indexes")
    void test1_newRepo_savedWithLatestCommitSha_notIndexed() {
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        Map<String, Object> remoteRepo = new HashMap<>();
        remoteRepo.put("id", 101L);
        remoteRepo.put("full_name", "octocat/new-repo");
        remoteRepo.put("owner", Map.of("login", "octocat"));
        remoteRepo.put("name", "new-repo");
        remoteRepo.put("private", false);
        remoteRepo.put("default_branch", "main");
        remoteRepo.put("language", "Java");
        remoteRepo.put("html_url", "https://github.com/octocat/new-repo");
        remoteRepo.put("description", "A brand new repo");

        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(List.of(remoteRepo));
        when(gitRepoRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "new-repo", "main"))
                .thenReturn("sha-head-new");

        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(1);
        assertThat(response.newRepositories()).isEqualTo(1);
        assertThat(response.updatedRepositories()).isEqualTo(0);
        assertThat(response.unchangedRepositories()).isEqualTo(0);
        assertThat(response.repositoriesWithNewCommits()).isEqualTo(1);

        ArgumentCaptor<GitRepo> captor = ArgumentCaptor.forClass(GitRepo.class);
        verify(gitRepoRepository).save(captor.capture());
        GitRepo saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getGithubRepoId()).isEqualTo(101L);
        assertThat(saved.getFullName()).isEqualTo("octocat/new-repo");
        assertThat(saved.getLatestCommitSha()).isEqualTo("sha-head-new");
        assertThat(saved.getIndexedCommitSha()).isNull();
        assertThat(saved.getIndexStatus()).isEqualTo(IndexStatus.PENDING);

        // CRITICAL: Verify IndexingService is NEVER called during Sync All
        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 2 — Sync All: existing repo with newer GitHub commit updates latestCommitSha without changing indexedCommitSha")
    void test2_existingRepo_newCommit_updatesLatestCommitSha_preservesIndexedCommitSha() {
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        GitRepo existingRepo = GitRepo.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .githubRepoId(202L)
                .owner("octocat")
                .name("existing-repo")
                .fullName("octocat/existing-repo")
                .defaultBranch("main")
                .indexStatus(IndexStatus.READY)
                .indexedCommitSha("sha-indexed-old")
                .latestCommitSha("sha-indexed-old")
                .build();

        Map<String, Object> remoteRepo = new HashMap<>();
        remoteRepo.put("id", 202L);
        remoteRepo.put("full_name", "octocat/existing-repo");
        remoteRepo.put("owner", Map.of("login", "octocat"));
        remoteRepo.put("name", "existing-repo");
        remoteRepo.put("private", false);
        remoteRepo.put("default_branch", "main");

        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(List.of(remoteRepo));
        when(gitRepoRepository.findByUserId(userId)).thenReturn(List.of(existingRepo));
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "existing-repo", "main"))
                .thenReturn("sha-github-new");

        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(1);
        assertThat(response.newRepositories()).isEqualTo(0);
        assertThat(response.updatedRepositories()).isEqualTo(1);
        assertThat(response.unchangedRepositories()).isEqualTo(0);
        assertThat(response.repositoriesWithNewCommits()).isEqualTo(1);

        // Verify latestCommitSha was updated, but indexedCommitSha was NOT changed
        assertThat(existingRepo.getLatestCommitSha()).isEqualTo("sha-github-new");
        assertThat(existingRepo.getIndexedCommitSha()).isEqualTo("sha-indexed-old");
        assertThat(existingRepo.getIndexStatus()).isEqualTo(IndexStatus.READY);

        // CRITICAL: Verify IndexingService is NEVER called during Sync All
        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 3 — Sync All: unchanged repo is detected")
    void test3_unchangedRepo_detected() {
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        GitRepo existingRepo = GitRepo.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .githubRepoId(303L)
                .owner("octocat")
                .name("same-repo")
                .fullName("octocat/same-repo")
                .defaultBranch("main")
                .indexStatus(IndexStatus.READY)
                .indexedCommitSha("sha-current")
                .latestCommitSha("sha-current")
                .build();

        Map<String, Object> remoteRepo = new HashMap<>();
        remoteRepo.put("id", 303L);
        remoteRepo.put("full_name", "octocat/same-repo");
        remoteRepo.put("owner", Map.of("login", "octocat"));
        remoteRepo.put("name", "same-repo");
        remoteRepo.put("private", false);
        remoteRepo.put("default_branch", "main");

        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(List.of(remoteRepo));
        when(gitRepoRepository.findByUserId(userId)).thenReturn(List.of(existingRepo));
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "same-repo", "main"))
                .thenReturn("sha-current");

        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(1);
        assertThat(response.newRepositories()).isEqualTo(0);
        assertThat(response.updatedRepositories()).isEqualTo(0);
        assertThat(response.unchangedRepositories()).isEqualTo(1);
        assertThat(response.repositoriesWithNewCommits()).isEqualTo(0);

        verify(indexingService, never()).startIndexing(any(), any());
        verify(indexingService, never()).indexAsync(any(), any());
    }

    @Test
    @DisplayName("Test 4 — Sync All: isolates user repositories, never modifies other users' repos")
    void test4_userIsolation() {
        UUID otherUserId = UUID.randomUUID();
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(Collections.emptyList());
        when(gitRepoRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(0);
        verify(gitRepoRepository).findByUserId(userId);
        verify(gitRepoRepository, never()).findByUserId(otherUserId);
    }

    @Test
    @DisplayName("Test 5 — Sync All: handles empty repository gracefully without failing")
    void test5_emptyRepo_handledGracefully() {
        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");

        Map<String, Object> remoteRepo = new HashMap<>();
        remoteRepo.put("id", 404L);
        remoteRepo.put("full_name", "octocat/empty-repo");
        remoteRepo.put("owner", Map.of("login", "octocat"));
        remoteRepo.put("name", "empty-repo");
        remoteRepo.put("private", false);
        remoteRepo.put("default_branch", "main");

        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(List.of(remoteRepo));
        when(gitRepoRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(gitHubApiClient.getLatestCommitSha("raw_token", "octocat", "empty-repo", "main"))
                .thenThrow(new IllegalStateException("Git Repository is empty"));

        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(1);
        assertThat(response.newRepositories()).isEqualTo(1);

        ArgumentCaptor<GitRepo> captor = ArgumentCaptor.forClass(GitRepo.class);
        verify(gitRepoRepository).save(captor.capture());
        GitRepo saved = captor.getValue();
        assertThat(saved.getLatestCommitSha()).isNull();
        assertThat(saved.getIndexedCommitSha()).isNull();
    }

    @Test
    @DisplayName("Test 6 — Sync All: executes persistence inside TransactionTemplate when configured")
    void test6_syncAllRepos_executesInsideTransactionTemplate_whenConfigured() {
        TransactionTemplate mockTxTemplate = mock(TransactionTemplate.class);
        when(mockTxTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        GitRepoService serviceWithTx = new GitRepoService(
                gitRepoRepository,
                userService,
                gitHubApiClient,
                mockTxTemplate
        );

        when(userService.getById(userId)).thenReturn(user);
        when(userService.decryptAccessToken(user)).thenReturn("raw_token");
        when(gitHubApiClient.listUserRepos("raw_token")).thenReturn(List.of());
        when(gitRepoRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        SyncAllReposResponse response = serviceWithTx.syncAllRepos(userId);

        assertThat(response.totalRepositories()).isEqualTo(0);
        verify(mockTxTemplate).execute(any());
    }
}
