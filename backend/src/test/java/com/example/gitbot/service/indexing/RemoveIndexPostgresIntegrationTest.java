package com.example.gitbot.service.indexing;

import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.ChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RemoveIndexPostgresIntegrationTest {

    @Autowired
    private IndexingService indexingService;

    @Autowired
    private GitRepoRepository gitRepoRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("Verify PostgreSQL Remove Index and Re-Index lifecycle")
    void testRemoveIndexAndReindexLifecycleInPostgres() {
        GitRepo testRepo = gitRepoRepository.findAll().stream()
                .filter(r -> !r.isDemo() && r.getIndexStatus() == IndexStatus.READY)
                .findFirst()
                .orElseGet(() -> {
                    GitRepo r = GitRepo.builder()
                            .name("test-repo")
                            .fullName("test/test-repo")
                            .userId(UUID.randomUUID())
                            .indexStatus(IndexStatus.READY)
                            .latestCommitSha("abc1234")
                            .indexedCommitSha("abc1234")
                            .chunkCount(5)
                            .build();
                    return gitRepoRepository.save(r);
                });
        UUID repoId = testRepo.getId();
        UUID userId = testRepo.getUserId();

        // 1. BEFORE: verify initial vector count
        Integer beforeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'repoId' = ?",
                Integer.class,
                repoId.toString()
        );
        assertThat(beforeCount).isNotNull();
        System.out.println("BEFORE Remove Index: vector count = " + beforeCount);

        // 2. Perform Remove Index
        GitRepo removedRepo = indexingService.removeIndex(repoId, userId);
        assertThat(removedRepo.getIndexStatus()).isEqualTo(IndexStatus.PENDING);
        assertThat(removedRepo.getChunkCount()).isZero();
        assertThat(removedRepo.getIndexedCommitSha()).isNull();

        // 3. AFTER Remove Index: count = 0
        Integer afterCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'repoId' = ?",
                Integer.class,
                repoId.toString()
        );
        System.out.println("AFTER Remove Index: vector count = " + afterCount);
        assertThat(afterCount).isZero();

        // 4. Verify git_repositories row still exists with PENDING invariant
        GitRepo repoInDb = gitRepoRepository.findById(repoId).orElseThrow();
        assertThat(repoInDb.getIndexStatus()).isEqualTo(IndexStatus.PENDING);
        assertThat(repoInDb.getIndexedCommitSha()).isNull();
        assertThat(repoInDb.getChunkCount()).isZero();
        assertThat(repoInDb.getLatestCommitSha()).isNotNull();

        // 5. Verify chat_sessions and chat_messages still exist
        var sessions = chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(sessions).isNotEmpty();
        long sessionCount = sessions.stream().filter(s -> s.getRepositoryId().equals(repoId)).count();
        assertThat(sessionCount).isGreaterThan(0);
        System.out.println("Verified chat_sessions still exist for repo: " + sessionCount);

        // 6. Verify generation is forbidden while PENDING
        UUID sessionId = sessions.stream().filter(s -> s.getRepositoryId().equals(repoId)).findFirst().get().getId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                chatService.streamReply(userId, sessionId, "test question while pending")
        ).isInstanceOf(com.example.gitbot.exception.ConflictException.class)
         .hasMessageContaining("This repository is not indexed. Index it again to continue chatting.");

        // 7. Re-index repository
        System.out.println("Re-indexing repository...");
        try {
            indexingService.startIndexing(repoId, userId);
            indexingService.doIndex(repoId, userId, UUID.randomUUID().toString());
        } catch (Exception e) {
            System.err.println("Re-indexing exception (e.g. GitHub rate limit / token): " + e.getMessage());
            // If live GitHub token has expired or rate limited, manually restore to READY for test assertion if needed
            throw e;
        }

        // 8. Verify post re-index
        Integer reindexedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'repoId' = ?",
                Integer.class,
                repoId.toString()
        );
        System.out.println("AFTER Re-index: vector count = " + reindexedCount);
        assertThat(reindexedCount).isGreaterThan(0);

        GitRepo reindexedRepo = gitRepoRepository.findById(repoId).orElseThrow();
        assertThat(reindexedRepo.getIndexStatus()).isEqualTo(IndexStatus.READY);
        assertThat(reindexedRepo.getIndexedCommitSha()).isNotNull();
        assertThat(reindexedRepo.getChunkCount()).isGreaterThan(0);

        // 9. Verify new chat generation works when READY
        var emitter = chatService.streamReply(userId, sessionId, "What does this repo do?");
        assertThat(emitter).isNotNull();
        System.out.println("Chat generation succeeded after re-indexing!");
    }
}
