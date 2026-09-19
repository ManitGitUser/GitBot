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
import com.example.gitbot.service.indexing.IndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitRepoService {

    private final GitRepoRepository gitRepoRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;
    private final IndexingService indexingService;

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Transactional
    public List<GitRepoResponse> syncAndListGitRepos(UUID userId) {

        User user = userService.getById(userId);
        String token = userService.decryptAccessToken(user);
        List<Map<String, Object>> remoteGitRepos = gitHubApiClient.listUserRepos(token);

        List<GitRepo> saved = new ArrayList<>();

        for (Map<String, Object> remote : remoteGitRepos) {

            Long githubRepoId = toLong(remote.get("id"));
            GitRepo repo = gitRepoRepository
                    .findByUserIdAndGithubRepoId(userId, githubRepoId)
                    .orElseGet(GitRepo::new);

            String fullName = String.valueOf(remote.get("full_name"));
            String[] parts = fullName.split("/", 2);

            repo.setUserId(userId);
            repo.setGithubRepoId(githubRepoId);
            repo.setOwner(parts.length > 0 ? parts[0] : String.valueOf(remote.get("owner")));
            repo.setName(parts.length > 1 ? parts[1] : String.valueOf(remote.get("name")));
            repo.setFullName(fullName);
            repo.setPrivate(Boolean.TRUE.equals(remote.get("private")));
            repo.setDefaultBranch(remote.get("default_branch") != null
                    ? String.valueOf(remote.get("default_branch"))
                    : "main");
            repo.setLanguage(remote.get("language") != null ? String.valueOf(remote.get("language")) : null);
            repo.setHtmlUrl(remote.get("html_url") != null ? String.valueOf(remote.get("html_url")) : null);
            repo.setDescription(remote.get("description") != null ? String.valueOf(remote.get("description")) : null);
            repo.setUpdatedAt(Instant.now());
            if (repo.getOwner() == null || repo.getOwner().isBlank()) {
                Object ownerObj = remote.get("owner");
                if (ownerObj instanceof Map<?, ?> ownerMap && ownerMap.get("login") != null) {
                    repo.setOwner(String.valueOf(ownerMap.get("login")));
                }
            }

            saved.add(gitRepoRepository.save(repo));
        }

        return saved.stream()
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .map(this::toResponse)
                .toList();
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

        String currentSha = gitHubApiClient.getLatestCommitSha(
                token, repo.getOwner(), repo.getName(), repo.getDefaultBranch());

        repo.setLatestCommitSha(currentSha);
        gitRepoRepository.save(repo);

        String indexedSha = repo.getIndexedCommitSha();

        if (indexedSha != null && indexedSha.equals(currentSha) && repo.getIndexStatus() == IndexStatus.READY) {
            return new SyncRepoResponse(
                    repo.getId(),
                    false,
                    "Repository is already up to date.",
                    currentSha,
                    indexedSha
            );
        }

        indexingService.startIndexing(repoId, userId);
        indexingService.indexAsync(repoId, userId);

        return new SyncRepoResponse(
                repo.getId(),
                true,
                "New commit detected. Update index started.",
                currentSha,
                indexedSha
        );
    }

    @Transactional
    public SyncAllReposResponse syncAllRepos(UUID userId) {
        User user = userService.getById(userId);
        String token = userService.decryptAccessToken(user);
        List<Map<String, Object>> remoteGitRepos = gitHubApiClient.listUserRepos(token);

        List<GitRepo> existingRepos = gitRepoRepository.findByUserId(userId);
        Map<Long, GitRepo> existingMap = existingRepos.stream()
                .filter(r -> r.getGithubRepoId() != null)
                .collect(Collectors.toMap(GitRepo::getGithubRepoId, Function.identity(), (a, b) -> a));

        int totalRepositories = remoteGitRepos.size();
        int newRepositories = 0;
        int updatedRepositories = 0;
        int unchangedRepositories = 0;
        int repositoriesWithNewCommits = 0;

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

            GitRepo repo = existingMap.get(githubRepoId);
            if (repo == null) {
                newRepositories++;
                GitRepo newRepo = GitRepo.builder()
                        .userId(userId)
                        .githubRepoId(githubRepoId)
                        .owner(owner)
                        .name(name)
                        .fullName(fullName)
                        .isPrivate(isPrivate)
                        .defaultBranch(defaultBranch)
                        .language(language)
                        .htmlUrl(htmlUrl)
                        .description(description)
                        .latestCommitSha(latestSha)
                        .indexedCommitSha(null)
                        .indexStatus(IndexStatus.PENDING)
                        .chunkCount(0)
                        .filesTotal(0)
                        .filesProcessed(0)
                        .build();

                if (latestSha != null) {
                    repositoriesWithNewCommits++;
                }
                gitRepoRepository.save(newRepo);
            } else {
                if (latestSha != null && !Objects.equals(latestSha, repo.getIndexedCommitSha())) {
                    repositoriesWithNewCommits++;
                }

                boolean changed = false;
                if (!Objects.equals(repo.getLatestCommitSha(), latestSha)) {
                    repo.setLatestCommitSha(latestSha);
                    changed = true;
                }
                if (!Objects.equals(repo.getOwner(), owner)) {
                    repo.setOwner(owner);
                    changed = true;
                }
                if (!Objects.equals(repo.getName(), name)) {
                    repo.setName(name);
                    changed = true;
                }
                if (!Objects.equals(repo.getFullName(), fullName)) {
                    repo.setFullName(fullName);
                    changed = true;
                }
                if (repo.isPrivate() != isPrivate) {
                    repo.setPrivate(isPrivate);
                    changed = true;
                }
                if (!Objects.equals(repo.getDefaultBranch(), defaultBranch)) {
                    repo.setDefaultBranch(defaultBranch);
                    changed = true;
                }
                if (!Objects.equals(repo.getLanguage(), language)) {
                    repo.setLanguage(language);
                    changed = true;
                }
                if (!Objects.equals(repo.getHtmlUrl(), htmlUrl)) {
                    repo.setHtmlUrl(htmlUrl);
                    changed = true;
                }
                if (!Objects.equals(repo.getDescription(), description)) {
                    repo.setDescription(description);
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
                repositoriesWithNewCommits
        );
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
    public List<GitRepoResponse> listStored(UUID userId) {
        return gitRepoRepository.findByUserIdOrderByFullNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}
