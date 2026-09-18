package com.example.gitbot.service;

import com.example.gitbot.dto.GitRepoResponse;
import com.example.gitbot.dto.IndexStatusResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GitRepoService {

    private final GitRepoRepository gitRepoRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;

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
                gitRepo.getErrorMessage());
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
