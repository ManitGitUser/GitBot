package com.example.gitbot.service.indexing;

import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.repository.GitRepoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingProgressService {

    private final GitRepoRepository gitRepoRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(
            UUID repoId,
            int total,
            int processed,
            int chunks,
            IndexStatus status,
            String error
    ) {
        gitRepoRepository.findById(repoId).ifPresent(repo -> {
            int validTotal = Math.max(0, total);
            int validProcessed = Math.max(0, Math.min(processed, validTotal));
            repo.setFilesTotal(validTotal);
            repo.setFilesProcessed(validProcessed);
            repo.setChunkCount(Math.max(0, chunks));
            repo.setIndexStatus(status);
            repo.setErrorMessage(error);
            gitRepoRepository.save(repo);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReady(UUID repoId, int totalFiles, int processedFiles, int totalChunks, String fullName) {
        gitRepoRepository.findById(repoId).ifPresent(repo -> {
            int validTotal = Math.max(0, totalFiles);
            int validProcessed = Math.max(0, Math.min(processedFiles, validTotal));
            repo.setIndexStatus(IndexStatus.READY);
            repo.setFilesTotal(validTotal);
            repo.setFilesProcessed(validProcessed);
            repo.setChunkCount(Math.max(0, totalChunks));
            repo.setIndexedAt(Instant.now());
            repo.setErrorMessage(null);
            gitRepoRepository.save(repo);
        });
        log.info("Indexed {} files ({} chunks) for {}", processedFiles, totalChunks, fullName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID repoId, String message) {
        gitRepoRepository.findById(repoId).ifPresent(repo -> {
            repo.setIndexStatus(IndexStatus.FAILED);
            repo.setErrorMessage(message != null && message.length() > 2000
                    ? message.substring(0, 2000)
                    : message);
            gitRepoRepository.save(repo);
        });
    }
}
