package com.example.gitbot.service;

import com.example.gitbot.dto.GitRepoResponse;
import com.example.gitbot.dto.IndexStatusResponse;
import com.example.gitbot.dto.SyncAllReposResponse;
import com.example.gitbot.dto.SyncRepoResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GitRepoService {

    private final GitRepoRepository gitRepoRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;
    private final TransactionTemplate transactionTemplate;

    public GitRepoService(
            GitRepoRepository gitRepoRepository,
            UserService userService,
            GitHubApiClient gitHubApiClient
    ) {
        this(gitRepoRepository, userService, gitHubApiClient, (TransactionTemplate) null);
    }

    @Autowired
    public GitRepoService(
            GitRepoRepository gitRepoRepository,
            UserService userService,
            GitHubApiClient gitHubApiClient,
            @Autowired(required = false) TransactionTemplate transactionTemplate
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.userService = userService;
        this.gitHubApiClient = gitHubApiClient;
        this.transactionTemplate = transactionTemplate;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    public GitRepoResponse toResponse(GitRepo gitRepo) {
        return new GitRepoResponse(
                gitRepo.getId(),
                gitRepo.getGithubRepoId(),
                gitRepo.getOwner(),
                gitRepo.getName(),
                gitRepo.getFullName(),
                gitRepo.isPrivate(),
                gitRepo.getDefaultBranch(),
                gitRepo.getLanguage(),
                gitRepo.getHtmlUrl(),
                gitRepo.getDescription(),
                gitRepo.getIndexStatus(),
                gitRepo.getIndexedAt(),
                gitRepo.getChunkCount(),
                gitRepo.getFilesTotal(),
                gitRepo.getFilesProcessed(),
                gitRepo.getErrorMessage(),
                gitRepo.getIndexedCommitSha(),
                gitRepo.getLatestCommitSha());
    }

    public SyncRepoResponse syncRepo(UUID repoId, UUID userId) {
        GitRepo repo = requireOwned(repoId, userId);

        User user = userService.getById(userId);
        String token = userService.decryptAccessToken(user);

        String currentSha = null;
        try {
            currentSha = gitHubApiClient.getLatestCommitSha(
                    token, repo.getOwner(), repo.getName(), repo.getDefaultBranch());
        } catch (Exception ex) {
            log.warn("Could not fetch latest commit SHA for repository {}: {}", repo.getFullName(), ex.getMessage());
        }

        repo.setLatestCommitSha(currentSha);
        repo.setUpdatedAt(Instant.now());
        gitRepoRepository.save(repo);

        String indexedSha = repo.getIndexedCommitSha();

        if (currentSha != null && currentSha.equals(indexedSha)) {
            return new SyncRepoResponse(
                    repo.getId(),
                    false,
                    "Repository is already up to date.",
                    currentSha,
                    indexedSha);
        }

        if (currentSha == null) {
            return new SyncRepoResponse(
                    repo.getId(),
                    false,
                    "Repository is empty or has no commits.",
                    null,
                    indexedSha);
        }

        return new SyncRepoResponse(
                repo.getId(),
                false,
                "New commit available. Click Index to update.",
                currentSha,
                indexedSha);
    }

    private record DiscoveredRepo(
            Long githubRepoId,
            String fullName,
            String owner,
            String name,
            boolean isPrivate,
            String defaultBranch,
            String language,
            String htmlUrl,
            String description,
            String latestSha
    ) {}

    public SyncAllReposResponse syncAllRepos(UUID userId) {
        // Phase A — outside DB transaction: GitHub I/O and in-memory preparation
        User user = userService.getById(userId);
        String token = userService.decryptAccessToken(user);
        List<Map<String, Object>> remoteGitRepos = gitHubApiClient.listUserRepos(token);

        List<DiscoveredRepo> discoveredRepos = new ArrayList<>(remoteGitRepos.size());
        for (Map<String, Object> remote : remoteGitRepos) {
            Long githubRepoId = toLong(remote.get("id"));
            String fullName = String.valueOf(remote.get("full_name"));
            String[] parts = fullName.split("/", 2);
            String owner = parts.length > 0 ? parts[0] : String.valueOf(remote.get("owner"));
            String name = parts.length > 1 ? parts[1] : String.valueOf(remote.get("name"));
            if (owner == null || owner.isBlank()) {
                Object ownerObj = remote.get("owner");
                if (ownerObj instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
                    owner = String.valueOf(ownerMap.get("login"));
                }
            }
            boolean isPrivate = Boolean.TRUE.equals(remote.get("private"));
            String defaultBranch = remote.get("default_branch") != null
                    ? String.valueOf(remote.get("default_branch"))
                    : "main";
            String language = remote.get("language") != null ? String.valueOf(remote.get("language")) : null;
            String htmlUrl = remote.get("html_url") != null ? String.valueOf(remote.get("html_url")) : null;
            String description = remote.get("description") != null ? String.valueOf(remote.get("description")) : null;

            String latestSha = null;
            try {
                latestSha = gitHubApiClient.getLatestCommitSha(token, owner, name, defaultBranch);
            } catch (Exception ex) {
                log.warn("Could not fetch latest commit SHA for {}/{}: {}", owner, name, ex.getMessage());
            }

            discoveredRepos.add(new DiscoveredRepo(
                    githubRepoId,
                    fullName,
                    owner,
                    name,
                    isPrivate,
                    defaultBranch,
                    language,
                    htmlUrl,
                    description,
                    latestSha
            ));
        }

        // Phase B — short database transaction: load existing repositories, upsert/persist state
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> persistSyncState(userId, discoveredRepos));
        } else {
            return persistSyncState(userId, discoveredRepos);
        }
    }

    private SyncAllReposResponse persistSyncState(UUID userId, List<DiscoveredRepo> discoveredRepos) {
        List<GitRepo> existingRepos = gitRepoRepository.findByUserId(userId);
        Map<Long, GitRepo> existingMap = existingRepos.stream()
                .filter(r -> r.getGithubRepoId() != null)
                .collect(Collectors.toMap(GitRepo::getGithubRepoId, Function.identity(), (a, b) -> a));

        int totalRepositories = discoveredRepos.size();
        int newRepositories = 0;
        int updatedRepositories = 0;
        int unchangedRepositories = 0;
        int repositoriesWithNewCommits = 0;

        for (DiscoveredRepo discovered : discoveredRepos) {
            GitRepo repo = existingMap.get(discovered.githubRepoId());
            if (repo == null) {
                newRepositories++;
                GitRepo newRepo = GitRepo.builder()
                        .userId(userId)
                        .githubRepoId(discovered.githubRepoId())
                        .owner(discovered.owner())
                        .name(discovered.name())
                        .fullName(discovered.fullName())
                        .isPrivate(discovered.isPrivate())
                        .defaultBranch(discovered.defaultBranch())
                        .language(discovered.language())
                        .htmlUrl(discovered.htmlUrl())
                        .description(discovered.description())
                        .latestCommitSha(discovered.latestSha())
                        .indexedCommitSha(null)
                        .indexStatus(IndexStatus.PENDING)
                        .chunkCount(0)
                        .filesTotal(0)
                        .filesProcessed(0)
                        .build();

                if (discovered.latestSha() != null) {
                    repositoriesWithNewCommits++;
                }
                gitRepoRepository.save(newRepo);
            } else {
                if (discovered.latestSha() != null && !Objects.equals(discovered.latestSha(), repo.getIndexedCommitSha())) {
                    repositoriesWithNewCommits++;
                }

                boolean changed = false;
                if (!Objects.equals(repo.getLatestCommitSha(), discovered.latestSha())) {
                    repo.setLatestCommitSha(discovered.latestSha());
                    changed = true;
                }
                if (!Objects.equals(repo.getOwner(), discovered.owner())) {
                    repo.setOwner(discovered.owner());
                    changed = true;
                }
                if (!Objects.equals(repo.getName(), discovered.name())) {
                    repo.setName(discovered.name());
                    changed = true;
                }
                if (!Objects.equals(repo.getFullName(), discovered.fullName())) {
                    repo.setFullName(discovered.fullName());
                    changed = true;
                }
                if (repo.isPrivate() != discovered.isPrivate()) {
                    repo.setPrivate(discovered.isPrivate());
                    changed = true;
                }
                if (!Objects.equals(repo.getDefaultBranch(), discovered.defaultBranch())) {
                    repo.setDefaultBranch(discovered.defaultBranch());
                    changed = true;
                }
                if (!Objects.equals(repo.getLanguage(), discovered.language())) {
                    repo.setLanguage(discovered.language());
                    changed = true;
                }
                if (!Objects.equals(repo.getHtmlUrl(), discovered.htmlUrl())) {
                    repo.setHtmlUrl(discovered.htmlUrl());
                    changed = true;
                }
                if (!Objects.equals(repo.getDescription(), discovered.description())) {
                    repo.setDescription(discovered.description());
                    changed = true;
                }

                if (changed) {
                    repo.setUpdatedAt(Instant.now());
                    gitRepoRepository.save(repo);
                    updatedRepositories++;
                } else {
                    unchangedRepositories++;
                }
            }
        }

        return new SyncAllReposResponse(
                totalRepositories,
                newRepositories,
                updatedRepositories,
                unchangedRepositories,
                repositoriesWithNewCommits);
    }

    @Transactional(readOnly = true)
    public GitRepo requireOwned(UUID repoId, UUID userId) {
        return gitRepoRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
    }

    @Transactional(readOnly = true)
    public IndexStatusResponse status(UUID repoId, UUID userId) {
        GitRepo repo = requireOwned(repoId, userId);
        return new IndexStatusResponse(
                repo.getId(),
                repo.getIndexStatus(),
                repo.getFilesTotal(),
                repo.getFilesProcessed(),
                repo.getChunkCount(),
                repo.getIndexedAt(),
                repo.getErrorMessage());
    }

    @Transactional(readOnly = true)
    public GitRepo getById(UUID repoId) {
        return gitRepoRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
    }


    @Transactional(readOnly = true)
    public com.example.gitbot.dto.PageResponse<GitRepoResponse> listStored(
            UUID userId,
            String status,
            String visibility,
            String search,
            org.springframework.data.domain.Pageable pageable) {
        Boolean isPrivate = null;
        if ("private".equalsIgnoreCase(visibility)) {
            isPrivate = true;
        } else if ("public".equalsIgnoreCase(visibility)) {
            isPrivate = false;
        }

        IndexStatus indexStatus = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                indexStatus = IndexStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        String query = (search != null && !search.isBlank()) ? search.trim() : null;

        org.springframework.data.domain.Page<GitRepo> page = gitRepoRepository.findWithFilters(
                userId, isPrivate, indexStatus, query, pageable);
        return com.example.gitbot.dto.PageResponse.of(page.map(this::toResponse));
    }
}
