package com.example.gitbot.service.indexing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.gitbot.service.ai.RagSettings;

/**
 * Lightweight code-aware, line-preserving chunker.
 *
 * <p>
 * Uses a two-level strategy:
 * <ol>
 * <li><b>Level 1:</b> Natural code boundaries (classes, methods, functions,
 * exports, types)
 * identified cheaply via language-specific patterns.</li>
 * <li><b>Level 2:</b> Deterministic line-based chunking with configurable
 * overlap when logical
 * units exceed target chunk size or for unsupported languages.</li>
 * </ol>
 *
 * <p>
 * Guarantees accurate 1-based {@code startLine} and {@code endLine} source
 * mapping.
 */
@Component
public class CodeChunker {

    private final CodeFileFilter fileFilter;
    private final int chunkSize;
    private final int chunkOverlap;

    private static final Map<String, Pattern> BOUNDARY_PATTERNS = new HashMap<>();

    static {
        // Java, Kotlin, C#, Scala
        Pattern jvmPattern = Pattern.compile(
                "^\\s*(public|protected|private|internal|abstract|final|sealed|static|override|default)?\\s*" +
                        "(class|interface|enum|record|object|data\\s+class|struct|fun|def|void|int|long|boolean|double|float|String|[A-Z]\\w*)\\b");
        BOUNDARY_PATTERNS.put("java", jvmPattern);
        BOUNDARY_PATTERNS.put("kt", jvmPattern);
        BOUNDARY_PATTERNS.put("kts", jvmPattern);
        BOUNDARY_PATTERNS.put("cs", jvmPattern);
        BOUNDARY_PATTERNS.put("scala", jvmPattern);

        // Python
        Pattern pyPattern = Pattern.compile("^\\s{0,4}(class|def|async\\s+def)\\s+\\w+");
        BOUNDARY_PATTERNS.put("py", pyPattern);

        // JavaScript, TypeScript
        Pattern jsPattern = Pattern.compile(
                "^\\s*(export\\s+)?(default\\s+)?(async\\s+)?(function|class|interface|type|const|let|var)\\s+\\w+");
        BOUNDARY_PATTERNS.put("js", jsPattern);
        BOUNDARY_PATTERNS.put("jsx", jsPattern);
        BOUNDARY_PATTERNS.put("ts", jsPattern);
        BOUNDARY_PATTERNS.put("tsx", jsPattern);
        BOUNDARY_PATTERNS.put("mjs", jsPattern);
        BOUNDARY_PATTERNS.put("cjs", jsPattern);

        // Go
        Pattern goPattern = Pattern.compile("^(func|type|var|const)\\b");
        BOUNDARY_PATTERNS.put("go", goPattern);

        // Rust
        Pattern rustPattern = Pattern
                .compile("^\\s*(pub(\\([^\\)]+\\))?\\s+)?(fn|struct|enum|trait|impl|type|const|static)\\b");
        BOUNDARY_PATTERNS.put("rs", rustPattern);

        // C / C++
        Pattern cPattern = Pattern
                .compile("^\\s*(class|struct|enum|union|namespace|template)\\b|^[a-zA-Z_]\\w*\\s+[a-zA-Z_]\\w*\\s*\\(");
        BOUNDARY_PATTERNS.put("c", cPattern);
        BOUNDARY_PATTERNS.put("h", cPattern);
        BOUNDARY_PATTERNS.put("cpp", cPattern);
        BOUNDARY_PATTERNS.put("hpp", cPattern);
    }

    public record SourceLine(int lineNumber, String text) {
    }

    @Autowired
    public CodeChunker(
            CodeFileFilter fileFilter,
            @Value("${app.indexing.chunk-size:800}") int chunkSize,
            @Value("${app.indexing.chunk-overlap:100}") int chunkOverlap) {
        this.fileFilter = fileFilter;
        this.chunkSize = Math.max(100, chunkSize);
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize / 2));
    }

    public List<Document> chunkFile(
            String repoId,
            String repoFullName,
            String filePath,
            String content,
            String runId) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String language = fileFilter.detectLanguage(filePath);
        List<SourceLine> lines = parseLines(content);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;
        int startIdx = 0;
        int totalLines = lines.size();

        while (startIdx < totalLines) {
            int endIdx = startIdx;
            int currentLength = lines.get(startIdx).text().length();

            while (endIdx + 1 < totalLines) {
                String nextLineText = lines.get(endIdx + 1).text();
                int nextLineLen = nextLineText.length() + 1; // +1 for newline

                // If next line is a natural code boundary and we have accumulated sufficient
                // context (>= 60% of target)
                if (currentLength >= (chunkSize * 0.6) && isBoundaryLine(language, nextLineText) && endIdx > startIdx) {
                    break;
                }

                // If adding next line exceeds target chunkSize and we already have at least 1
                // line
                if (currentLength + nextLineLen > chunkSize && endIdx > startIdx) {
                    break;
                }

                currentLength += nextLineLen;
                endIdx++;
            }

            int startLine = lines.get(startIdx).lineNumber();
            int endLine = lines.get(endIdx).lineNumber();

            StringBuilder code = new StringBuilder();
            for (int k = startIdx; k <= endIdx; k++) {
                if (k > startIdx) {
                    code.append("\n");
                }
                code.append(lines.get(k).text());
            }

            String lineHeader = startLine == endLine
                    ? "// File: " + filePath + " (line " + startLine + ")\n"
                    : "// File: " + filePath + " (lines " + startLine + "-" + endLine + ")\n";
            String docText = lineHeader + code;

            Map<String, Object> metadata = new HashMap<>();
            metadata.put(RagSettings.METADATA_REPO_ID, repoId);
            if (repoFullName != null && !repoFullName.isBlank()) {
                metadata.put("repoFullName", repoFullName);
            }
            metadata.put("filePath", filePath);
            metadata.put("language", language);
            metadata.put("chunkIndex", chunkIndex++);
            metadata.put("startLine", startLine);
            metadata.put("endLine", endLine);
            if (runId != null && !runId.isBlank()) {
                metadata.put("runId", runId);
            }

            chunks.add(new Document(docText, metadata));

            if (endIdx >= totalLines - 1) {
                break;
            }

            // Calculate next start index with overlap
            int nextStartIdx = endIdx + 1;
            if (chunkOverlap > 0) {
                int overlapAccum = 0;
                int candidate = endIdx;
                while (candidate > startIdx) {
                    overlapAccum += lines.get(candidate).text().length() + 1;
                    if (overlapAccum >= chunkOverlap) {
                        break;
                    }
                    candidate--;
                }
                nextStartIdx = Math.max(startIdx + 1, candidate);
            }

            startIdx = nextStartIdx;
        }

        return chunks;
    }

    public static List<SourceLine> parseLines(String content) {
        List<SourceLine> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }

        int lineNum = 1;
        int start = 0;
        int len = content.length();

        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '\r') {
                String lineText = content.substring(start, i);
                lines.add(new SourceLine(lineNum++, lineText));
                if (i + 1 < len && content.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            } else if (c == '\n') {
                String lineText = content.substring(start, i);
                lines.add(new SourceLine(lineNum++, lineText));
                start = i + 1;
            }
        }

        if (start < len) {
            lines.add(new SourceLine(lineNum, content.substring(start)));
        }

        return lines;
    }

    private boolean isBoundaryLine(String language, String lineText) {
        if (language == null || lineText == null || lineText.isBlank()) {
            return false;
        }
        Pattern pattern = BOUNDARY_PATTERNS.get(language.toLowerCase(Locale.ROOT));
        if (pattern == null) {
            return false;
        }
        return pattern.matcher(lineText).find();
    }
}
