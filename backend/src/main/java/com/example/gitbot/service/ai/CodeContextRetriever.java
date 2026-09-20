package com.example.gitbot.service.ai;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.gitbot.dto.CitationDto;
import com.example.gitbot.dto.RetrievedContextDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Code context retriever for RAG.
 *
 * <p>
 * Key Guarantees:
 * <ul>
 * <li><b>Strict Repository Isolation:</b> All vector, neighbor, and fallback
 * queries are
 * scoped to {@code repositoryId} via {@code repoId} metadata filters.</li>
 * <li><b>Configurable Top-K & Budget:</b> Limits retrieval to {@code topK} and
 * caps total
 * context at {@code maxContextChars}.</li>
 * <li><b>Highest-Rank Guarantee:</b> Primary results are processed first. The
 * #1 primary
 * result is never dropped; if it alone exceeds {@code maxContextChars}, it is
 * truncated.</li>
 * <li><b>Stable Deduplication:</b> Chunks are deduplicated by
 * {@code (repoId, filePath, chunkIndex)}.</li>
 * <li><b>Lightweight Neighbors:</b> Adjacent chunks ({@code chunkIndex ± 1})
 * from top hits are
 * retrieved via direct SQL (avoiding extra embedding calls) if budget
 * remains.</li>
 * <li><b>Lightweight Lexical Fallback:</b> If vector search returns 0 matches
 * for a query containing
 * code identifiers, performs an exact SQL {@code ILIKE} search scoped to the
 * repository.</li>
 * <li><b>Structured Formatting:</b> Clear {@code <source>} XML tags with file
 * paths, line ranges,
 * and markdown fences.</li>
 * </ul>
 */
@Service
@Slf4j
public class CodeContextRetriever {

    private static final String NO_MATCHES = "( unfortunately no matching code chunks found, try something else buddy )";

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "with",
            "from", "by", "about", "what", "where", "when", "why", "how", "who", "which",
            "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do",
            "does", "did", "can", "could", "should", "would", "will", "this", "that", "these",
            "those", "code", "repo", "repository", "file", "work", "works");

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]{2,}\\b");

    public record ChunkKey(String repoId, String filePath, Integer chunkIndex) {
        public static ChunkKey from(Document doc, String defaultRepoId) {
            var meta = doc.getMetadata();
            String repoId = meta.get(RagSettings.METADATA_REPO_ID) != null
                    ? String.valueOf(meta.get(RagSettings.METADATA_REPO_ID))
                    : defaultRepoId;
            String filePath = meta.get("filePath") != null
                    ? String.valueOf(meta.get("filePath"))
                    : "";
            Integer chunkIndex = null;
            Object idxObj = meta.get("chunkIndex");
            if (idxObj instanceof Number n) {
                chunkIndex = n.intValue();
            } else if (idxObj != null) {
                try {
                    chunkIndex = Integer.parseInt(idxObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            return new ChunkKey(repoId, filePath, chunkIndex);
        }
    }

    private final VectorStore vectorStore;
    private final CitationMapper citationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final int topK;
    private final int maxContextChars;
    private final boolean enableNeighboring;
    private final int maxNeighboringChunks;

    @Autowired
    public CodeContextRetriever(
            VectorStore vectorStore,
            CitationMapper citationMapper,
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) JsonMapper jsonMapper,
            @Value("${app.rag.top-k:10}") int topK,
            @Value("${app.rag.max-context-chars:12000}") int maxContextChars,
            @Value("${app.rag.enable-neighboring:true}") boolean enableNeighboring,
            @Value("${app.rag.max-neighboring-chunks:4}") int maxNeighboringChunks) {
        this.vectorStore = vectorStore;
        this.citationMapper = citationMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonMapper.builder().build();
        this.topK = Math.max(1, topK);
        this.maxContextChars = Math.max(100, maxContextChars);
        this.enableNeighboring = enableNeighboring;
        this.maxNeighboringChunks = Math.max(0, maxNeighboringChunks);
    }

    public RetrievedContextDto retrieve(UUID repositoryId, String question) {
        long ragStart = System.nanoTime();
        String normalizedQuestion = normalizeQuery(question);
        if (normalizedQuestion.isBlank()) {
            return new RetrievedContextDto(List.of(), NO_MATCHES);
        }

        // 1. Strict repository-scoped vector search
        var filter = new FilterExpressionBuilder()
                .eq(RagSettings.METADATA_REPO_ID, repositoryId.toString())
                .build();

        var search = SearchRequest.builder()
                .query(normalizedQuestion)
                .topK(topK)
                .filterExpression(filter)
                .build();

        long vectorSearchStart = System.nanoTime();
        List<Document> rawVectorResults = vectorStore.similaritySearch(search);
        long vectorSearchDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - vectorSearchStart);
        if (rawVectorResults == null) {
            rawVectorResults = List.of();
        }

        // Defensive verification: discard any chunks with mismatched repoId
        List<Document> primaryDocs = rawVectorResults.stream()
                .filter(d -> {
                    Object rId = d.getMetadata().get(RagSettings.METADATA_REPO_ID);
                    return rId == null || repositoryId.toString().equals(String.valueOf(rId));
                })
                .toList();

        // 2. Lightweight lexical fallback if vector search returned 0 results
        if (primaryDocs.isEmpty()) {
            primaryDocs = fallbackLexicalSearch(repositoryId, normalizedQuestion);
        }

        if (primaryDocs.isEmpty()) {
            long ragTotalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ragStart);
            return new RetrievedContextDto(List.of(), NO_MATCHES, vectorSearchDurationMs, 0, ragTotalDurationMs);
        }

        // 3. Process primary results with Highest-Rank Guarantee & Context Budget
        List<Document> acceptedPrimaryDocs = new ArrayList<>();
        List<String> formattedBlocks = new ArrayList<>();
        Set<ChunkKey> seenKeys = new HashSet<>();
        int currentBudgetUsed = 0;

        for (Document doc : primaryDocs) {
            ChunkKey key = ChunkKey.from(doc, repositoryId.toString());
            if (seenKeys.contains(key)) {
                continue;
            }

            String block = formatSourceBlock(doc);
            int blockSize = block.length();

            if (acceptedPrimaryDocs.isEmpty()) {
                // Guaranteed: #1 primary result is NEVER dropped. Truncate if it exceeds
                // budget.
                if (blockSize > maxContextChars) {
                    block = truncateSourceBlock(doc, maxContextChars);
                    blockSize = block.length();
                }
                acceptedPrimaryDocs.add(doc);
                formattedBlocks.add(block);
                seenKeys.add(key);
                currentBudgetUsed += blockSize + 2; // +2 for separator \n\n
            } else {
                // Remaining primary results added in rank order if budget permits
                if (currentBudgetUsed + blockSize + 2 <= maxContextChars) {
                    acceptedPrimaryDocs.add(doc);
                    formattedBlocks.add(block);
                    seenKeys.add(key);
                    currentBudgetUsed += blockSize + 2;
                }
            }
        }

        // 4. Retrieve adjacent neighboring chunks only if space remains
        List<Document> acceptedNeighbors = new ArrayList<>();
        long neighborDurationMs = 0;
        if (enableNeighboring && jdbcTemplate != null && currentBudgetUsed < maxContextChars) {
            long neighborStart = System.nanoTime();
            List<Document> candidateNeighbors = findNeighborChunks(repositoryId, acceptedPrimaryDocs, seenKeys);
            neighborDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - neighborStart);
            for (Document neighbor : candidateNeighbors) {
                if (acceptedNeighbors.size() >= maxNeighboringChunks) {
                    break;
                }
                ChunkKey nKey = ChunkKey.from(neighbor, repositoryId.toString());
                if (seenKeys.contains(nKey)) {
                    continue;
                }

                String nBlock = formatSourceBlock(neighbor);
                int nBlockSize = nBlock.length();

                if (currentBudgetUsed + nBlockSize + 2 <= maxContextChars) {
                    acceptedNeighbors.add(neighbor);
                    formattedBlocks.add(nBlock);
                    seenKeys.add(nKey);
                    currentBudgetUsed += nBlockSize + 2;
                }
            }
        }

        // 5. Build citations corresponding strictly to all accepted source chunks
        List<Document> allAcceptedDocs = new ArrayList<>(acceptedPrimaryDocs);
        allAcceptedDocs.addAll(acceptedNeighbors);

        List<CitationDto> citations = allAcceptedDocs.stream()
                .map(citationMapper::fromDocument)
                .distinct()
                .toList();

        String contextText = String.join("\n\n", formattedBlocks);
        if (contextText.isBlank()) {
            contextText = NO_MATCHES;
        }

        long ragTotalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ragStart);
        return new RetrievedContextDto(citations, contextText, vectorSearchDurationMs, neighborDurationMs, ragTotalDurationMs);
    }

    String normalizeQuery(String question) {
        if (question == null) {
            return "";
        }
        return question.strip().replaceAll("[\\t\\r\\n ]+", " ");
    }

    String formatSourceBlock(Document doc) {
        var meta = doc.getMetadata();
        String filePath = meta.get("filePath") != null ? String.valueOf(meta.get("filePath")) : "unknown";
        Object startLine = meta.get("startLine");
        Object endLine = meta.get("endLine");
        String language = meta.get("language") != null ? String.valueOf(meta.get("language")) : "";

        String linesStr;
        if (startLine != null && endLine != null) {
            linesStr = startLine.equals(endLine) ? String.valueOf(startLine) : (startLine + "-" + endLine);
        } else if (startLine != null) {
            linesStr = String.valueOf(startLine);
        } else {
            linesStr = "unknown";
        }

        String code = extractRawCode(doc.getText());

        return """
                <source>
                file: %s
                lines: %s

                ```%s
                %s
                ```
                </source>""".formatted(filePath, linesStr, language, code.stripTrailing());
    }

    String truncateSourceBlock(Document doc, int maxBudget) {
        var meta = doc.getMetadata();
        String filePath = meta.get("filePath") != null ? String.valueOf(meta.get("filePath")) : "unknown";
        Object startLine = meta.get("startLine");
        Object endLine = meta.get("endLine");
        String language = meta.get("language") != null ? String.valueOf(meta.get("language")) : "";

        String linesStr;
        if (startLine != null && endLine != null) {
            linesStr = startLine.equals(endLine) ? String.valueOf(startLine) : (startLine + "-" + endLine);
        } else if (startLine != null) {
            linesStr = String.valueOf(startLine);
        } else {
            linesStr = "unknown";
        }

        String code = extractRawCode(doc.getText());
        String header = "<source>\nfile: " + filePath + "\nlines: " + linesStr + "\n\n```" + language + "\n";
        String footer = "\n// ... [truncated to fit context budget]\n```\n</source>";

        int available = maxBudget - header.length() - footer.length();
        if (available > 0 && available < code.length()) {
            code = code.substring(0, available);
            return header + code + footer;
        } else if (available <= 0) {
            String minimal = "<source>\nfile: " + filePath + "\nlines: " + linesStr + "\n</source>";
            return minimal.length() <= maxBudget ? minimal : minimal.substring(0, Math.max(1, maxBudget));
        }
        return header + code + "\n```\n</source>";
    }

    private String extractRawCode(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith("// File: ")) {
            int newlineIdx = text.indexOf('\n');
            if (newlineIdx >= 0) {
                return text.substring(newlineIdx + 1);
            }
        }
        return text;
    }

    private record NeighborTarget(String filePath, int chunkIndex) {}

    private List<Document> findNeighborChunks(UUID repositoryId, List<Document> primaryDocs, Set<ChunkKey> seenKeys) {
        if (primaryDocs.isEmpty() || jdbcTemplate == null) {
            return List.of();
        }

        // Inspect top 2 primary documents for adjacent candidates
        int inspectCount = Math.min(2, primaryDocs.size());
        List<NeighborTarget> targets = new ArrayList<>(4);
        Set<NeighborTarget> candidateSeen = new HashSet<>();

        for (int i = 0; i < inspectCount; i++) {
            Document doc = primaryDocs.get(i);
            var meta = doc.getMetadata();
            if (meta == null) {
                continue;
            }
            String filePath = meta.get("filePath") != null ? String.valueOf(meta.get("filePath")) : null;
            Object idxObj = meta.get("chunkIndex");

            if (filePath == null || idxObj == null) {
                continue;
            }

            int chunkIndex;
            if (idxObj instanceof Number n) {
                chunkIndex = n.intValue();
            } else {
                try {
                    chunkIndex = Integer.parseInt(idxObj.toString());
                } catch (NumberFormatException e) {
                    continue;
                }
            }

            int prevIdx = chunkIndex - 1;
            int nextIdx = chunkIndex + 1;

            if (prevIdx >= 0 && !seenKeys.contains(new ChunkKey(repositoryId.toString(), filePath, prevIdx))) {
                NeighborTarget t = new NeighborTarget(filePath, prevIdx);
                if (candidateSeen.add(t)) {
                    targets.add(t);
                }
            }
            if (!seenKeys.contains(new ChunkKey(repositoryId.toString(), filePath, nextIdx))) {
                NeighborTarget t = new NeighborTarget(filePath, nextIdx);
                if (candidateSeen.add(t)) {
                    targets.add(t);
                }
            }
        }

        if (targets.isEmpty()) {
            return List.of();
        }

        return fetchNeighborChunksBatched(repositoryId, targets);
    }

    private List<Document> fetchNeighborChunksBatched(UUID repositoryId, List<NeighborTarget> targets) {
        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT content, metadata FROM vector_store
                    WHERE metadata->>'repoId' = ?
                      AND (
                    """);
            List<Object> params = new ArrayList<>();
            params.add(repositoryId.toString());

            for (int i = 0; i < targets.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append("(metadata->>'filePath' = ? AND (metadata->>'chunkIndex')::int = ?)");
                params.add(targets.get(i).filePath());
                params.add(targets.get(i).chunkIndex());
            }
            sql.append(")");

            List<Document> docs = jdbcTemplate.query(
                    sql.toString(),
                    (rs, rowNum) -> mapRowToDocument(rs),
                    params.toArray()
            );

            Map<NeighborTarget, Document> fetchedMap = new HashMap<>();
            for (Document doc : docs) {
                var meta = doc.getMetadata();
                if (meta != null) {
                    String fp = meta.get("filePath") != null ? String.valueOf(meta.get("filePath")) : null;
                    Object idx = meta.get("chunkIndex");
                    if (fp != null && idx != null) {
                        try {
                            int ci = (idx instanceof Number n) ? n.intValue() : Integer.parseInt(idx.toString());
                            fetchedMap.put(new NeighborTarget(fp, ci), doc);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            List<Document> orderedNeighbors = new ArrayList<>(targets.size());
            for (NeighborTarget target : targets) {
                Document doc = fetchedMap.get(target);
                if (doc != null) {
                    orderedNeighbors.add(doc);
                }
            }
            return orderedNeighbors;
        } catch (Exception ex) {
            log.debug("Could not fetch neighbor chunks in batch for repo {}: {}", repositoryId, ex.getMessage());
            return List.of();
        }
    }

    private List<Document> fallbackLexicalSearch(UUID repositoryId, String query) {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String searchToken = extractSearchToken(query);
        if (searchToken == null || searchToken.isBlank()) {
            return List.of();
        }

        try {
            String sql = """
                    SELECT content, metadata FROM vector_store
                    WHERE metadata->>'repoId' = ?
                      AND content ILIKE ?
                    LIMIT ?
                    """;
            String pattern = "%" + searchToken + "%";
            int limit = Math.min(5, topK);
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToDocument(rs),
                    repositoryId.toString(), pattern, limit);
        } catch (Exception ex) {
            log.debug("Lexical fallback search error for repo {}: {}", repositoryId, ex.getMessage());
            return List.of();
        }
    }

    private String extractSearchToken(String query) {
        Matcher matcher = IDENTIFIER_PATTERN.matcher(query);
        String candidate = null;
        while (matcher.find()) {
            String token = matcher.group();
            String lower = token.toLowerCase(Locale.ROOT);
            if (!STOP_WORDS.contains(lower)) {
                // If it looks like CamelCase, PascalCase, or snake_case, prefer it immediately
                if (token.contains("_") || !token.equals(token.toLowerCase(Locale.ROOT))) {
                    return token;
                }
                if (candidate == null || token.length() > candidate.length()) {
                    candidate = token;
                }
            }
        }
        return candidate;
    }

    private Document mapRowToDocument(ResultSet rs) throws SQLException {
        String content = rs.getString("content");
        String metaJson = rs.getString("metadata");
        Map<String, Object> metadata = new HashMap<>();
        if (metaJson != null && !metaJson.isBlank()) {
            try {
                metadata = jsonMapper.readValue(metaJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception ex) {
                log.debug("Failed to deserialize metadata JSON: {}", ex.getMessage());
            }
        }
        return new Document(content != null ? content : "", metadata);
    }
}
