package com.example.gitbot.service.indexing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.service.ai.RagSettings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.client.HttpStatusCodeException;

import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.UserService;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.github.GitHubRateLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Repository code indexing service.
 *
 * <p>
 * Uses a deterministic run-based strategy:
 * <ul>
 * <li>Each indexing execution generates a unique {@code runId}.</li>
 * <li>All vectors created during the run are tagged with {@code runId}.</li>
 * <li>On successful completion, old vectors from prior runs
 * ({@code runId != currentRunId})
 * are purged, and the repository is marked {@link IndexStatus#READY}.</li>
 * <li>On failure, only partial vectors from the failed {@code runId} are
 * deleted, and
 * the repository is marked {@link IndexStatus#FAILED}.</li>
 * <li>On server restart, dangling {@link IndexStatus#INDEXING} jobs are reset
 * to {@code FAILED}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingService {

    private static final int VECTOR_BATCH_SIZE = 32;
    private static final int PROGRESS_EVERY_N_FILES = 5;

    private final GitRepoRepository gitRepoRepository;
    private final UserService userService;
    private final GitHubApiClient gitHubApiClient;
    private final CodeFileFilter fileFilter;
    private final CodeChunker codeChunker;
    private final GitHubRateLimiter rateLimiter;
    private final VectorStore vectorStore;
    private final IndexingProgressService progressService;

    @Value("${app.indexing.max-file-bytes:102400}")
    private long maxFileBytes;

    public GitRepo startIndexing(UUID repoId, UUID userId) {
        GitRepo repo = gitRepoRepository.findByIdAndUserId(repoId, userId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));

        if (repo.getIndexStatus() == IndexStatus.INDEXING) {
            throw new BadRequestException("Repository is already being indexed");
        }

        repo.setIndexStatus(IndexStatus.INDEXING);
        repo.setFilesProcessed(0);
        repo.setFilesTotal(0);
        repo.setChunkCount(0);
        repo.setErrorMessage(null);
        return gitRepoRepository.save(repo);
    }

    @Async("indexingExecutor")
    public void indexAsync(UUID repoId, UUID userId) {
        String runId = UUID.randomUUID().toString();
        try {
            doIndex(repoId, userId, runId);
        } catch (Exception ex) {
            log.error("Indexing failed for repo {} (run {})", repoId, runId, ex);
            deleteVectorsForRun(repoId.toString(), runId);
            progressService.markFailed(repoId, ex.getMessage());
        }
    }

    void doIndex(UUID repoId, UUID userId, String runId) {
        GitRepo repo = gitRepoRepository.findById(repoId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        String token = userService.decryptAccessToken(userService.getById(userId));

        Map<String, Object> tree = gitHubApiClient.getRepoTree(
                token, repo.getOwner(), repo.getName(), repo.getDefaultBranch());

        if (Boolean.TRUE.equals(tree.get("truncated"))) {
            log.warn("GitHub tree response was truncated for repository {}. Some files may not be indexed.",
                    repo.getFullName());
        }

        List<String> filePaths = listIndexableFiles(tree);

        if (filePaths.isEmpty()) {
            deleteExistingVectors(repoId.toString());
            progressService.markReady(repoId, 0, 0, 0, repo.getFullName());
            return;
        }

        progressService.updateProgress(repoId, filePaths.size(), 0, 0, IndexStatus.INDEXING, null);

        List<Document> batch = new ArrayList<>();
        int processed = 0;
        int totalChunks = 0;
        int skippedFiles = 0;

        for (String path : filePaths) {
            try {
                String content = gitHubApiClient.getFileContent(
                        token, repo.getOwner(), repo.getName(), path);
                if (content != null && !content.isBlank()) {
                    List<Document> chunks = codeChunker.chunkFile(
                            repoId.toString(), repo.getFullName(), path, content, runId);
                    batch.addAll(chunks);
                    totalChunks += chunks.size();
                    if (batch.size() >= VECTOR_BATCH_SIZE) {
                        vectorStore.add(batch);
                        batch.clear();
                    }
                }
            } catch (HttpStatusCodeException ex) {
                // If 401 Unauthorized or 403 Forbidden/Rate-limited, fail fast!
                if (ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)
                        || ex.getStatusCode().isSameCodeAs(HttpStatus.FORBIDDEN)) {
                    throw ex;
                }
                skippedFiles++;
                log.warn("Skipping file {} in {}: {}", path, repo.getFullName(), ex.getMessage());
            } catch (Exception ex) {
                // If vectorStore threw an exception, fail the indexing job!
                if (ex.getClass().getName().contains("vectorstore")
                        || ex.getClass().getName().contains("VectorStore")) {
                    throw ex;
                }
                skippedFiles++;
                log.warn("Skipping file {} in {}: {}", path, repo.getFullName(), ex.getMessage());
            }

            processed++;
            if (processed % PROGRESS_EVERY_N_FILES == 0 || processed == filePaths.size()) {
                progressService.updateProgress(repoId, filePaths.size(), processed, totalChunks, IndexStatus.INDEXING,
                        null);
            }
            rateLimiter.pause();
        }

        if (!batch.isEmpty()) {
            vectorStore.add(batch);
            batch.clear();
        }

        if (totalChunks == 0 && !filePaths.isEmpty()) {
            throw new IllegalStateException(
                    "Failed to extract any code chunks from repository files (" + skippedFiles + " files skipped)");
        }

        // Success: atomically clean up any old vectors from previous runs
        deleteVectorsExceptRun(repoId.toString(), runId);

        progressService.markReady(repoId, filePaths.size(), processed, totalChunks, repo.getFullName());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetDanglingIndexingJobs() {
        try {
            List<GitRepo> dangling = gitRepoRepository.findByIndexStatus(IndexStatus.INDEXING);
            for (GitRepo repo : dangling) {
                log.warn("Resetting interrupted indexing job for repo {}", repo.getFullName());
                deleteExistingVectors(repo.getId().toString());
                progressService.markFailed(repo.getId(),
                        "Indexing was interrupted by server restart. Please re-index.");
            }
        } catch (Exception ex) {
            log.warn("Could not reset dangling indexing jobs on startup: {}", ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> listIndexableFiles(Map<String, Object> tree) {
        if (tree == null || tree.get("tree") == null) {
            return List.of();
        }

        List<Map<String, Object>> entries = (List<Map<String, Object>>) tree.get("tree");
        return entries.stream()
                .filter(entry -> "blob".equals(String.valueOf(entry.get("type"))))
                .filter(entry -> {
                    String path = String.valueOf(entry.get("path"));
                    long size = entry.get("size") instanceof Number n ? n.longValue() : 0L;
                    return fileFilter.isEligible(path, size, maxFileBytes);
                })
                .map(entry -> String.valueOf(entry.get("path")))
                .toList();
    }

    void deleteVectorsExceptRun(String repoId, String currentRunId) {
        try {
            var b = new FilterExpressionBuilder();
            var filter = b.and(
                    b.eq(RagSettings.METADATA_REPO_ID, repoId),
                    b.ne("runId", currentRunId)).build();
            vectorStore.delete(filter);
        } catch (Exception ex) {
            log.warn("Could not purge old vectors for repo {} after run {}: {}", repoId, currentRunId, ex.getMessage());
        }
    }

    void deleteVectorsForRun(String repoId, String runId) {
        try {
            var b = new FilterExpressionBuilder();
            var filter = b.and(
                    b.eq(RagSettings.METADATA_REPO_ID, repoId),
                    b.eq("runId", runId)).build();
            vectorStore.delete(filter);
        } catch (Exception ex) {
            log.warn("Could not delete partial vectors for repo {} run {}: {}", repoId, runId, ex.getMessage());
        }
    }

    public void deleteExistingVectors(String repoId) {
        try {
            var filter = new FilterExpressionBuilder().eq(RagSettings.METADATA_REPO_ID, repoId).build();
            vectorStore.delete(filter);
        } catch (Exception ex) {
            log.warn("Could not delete existing vectors for repo {}: {}", repoId, ex.getMessage());
        }
    }
}
