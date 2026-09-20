package com.example.gitbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes secondary expression indexes on the Spring AI vector_store table.
 *
 * <p>
 * Spring AI's {@link VectorStore} (PgVectorStore) exclusively manages the
 * creation
 * and schema validation of the {@code vector_store} table and the primary HNSW
 * embedding
 * index during bean initialization.
 *
 * <p>
 * Injecting {@link VectorStore} explicitly guarantees that Spring AI has
 * completed
 * table creation and schema validation before this runner executes.
 */
@Slf4j
@Component
public class VectorStoreIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public VectorStoreIndexInitializer(JdbcTemplate jdbcTemplate, VectorStore vectorStore) {
        this.jdbcTemplate = jdbcTemplate;
        // vectorStore is accepted to guarantee PgVectorStore initializes the table and
        // primary index first
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Ensuring idx_vector_store_repo_id expression index exists on vector_store...");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_vector_store_repo_id ON vector_store ((metadata->>'repoId'))");
        log.info("idx_vector_store_repo_id expression index verified successfully.");
    }
}
