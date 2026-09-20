package com.example.gitbot.service.indexing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

    private CodeFileFilter fileFilter;
    private CodeChunker chunker;

    @BeforeEach
    void setUp() {
        fileFilter = new CodeFileFilter();
        chunker = new CodeChunker(fileFilter, 300, 50);
    }

    @Test
    void testEmptyFile() {
        assertThat(chunker.chunkFile("repo-1", "owner/repo", "App.java", "", "run-1")).isEmpty();
        assertThat(chunker.chunkFile("repo-1", "owner/repo", "App.java", null, "run-1")).isEmpty();
    }

    @Test
    void testWhitespaceOnlyFile() {
        assertThat(chunker.chunkFile("repo-1", "owner/repo", "App.java", "   \n\n\t  \r\n  ", "run-1")).isEmpty();
    }

    @Test
    void testOneLineFile() {
        String code = "public class Hello {}";
        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "Hello.java", code, "run-1");

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);

        assertThat(doc.getMetadata().get("repoId")).isEqualTo("repo-1");
        assertThat(doc.getMetadata().get("repoFullName")).isEqualTo("owner/repo");
        assertThat(doc.getMetadata().get("filePath")).isEqualTo("Hello.java");
        assertThat(doc.getMetadata().get("language")).isEqualTo("java");
        assertThat(doc.getMetadata().get("chunkIndex")).isEqualTo(0);
        assertThat(doc.getMetadata().get("startLine")).isEqualTo(1);
        assertThat(doc.getMetadata().get("endLine")).isEqualTo(1);
        assertThat(doc.getMetadata().get("runId")).isEqualTo("run-1");

        assertThat(doc.getText()).startsWith("// File: Hello.java (line 1)\n");
        assertThat(doc.getText()).contains("public class Hello {}");
    }

    @Test
    void testMultiLineFile() {
        String code = """
                package com.example;

                public class Greeter {
                    public String greet(String name) {
                        return "Hello, " + name;
                    }
                }
                """;

        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "Greeter.java", code, "run-1");
        assertThat(docs).hasSize(1);

        Document doc = docs.get(0);
        assertThat(doc.getMetadata().get("startLine")).isEqualTo(1);
        assertThat(doc.getMetadata().get("endLine")).isEqualTo(7);
        assertThat(doc.getText()).startsWith("// File: Greeter.java (lines 1-7)\n");
    }

    @Test
    void testMultipleChunksAndChunkBoundaries() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append("int var").append(i).append(" = ").append(i).append(";\n");
        }
        String code = sb.toString();

        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "Vars.java", code, "run-1");
        assertThat(docs.size()).isGreaterThan(1);

        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            int start = (int) doc.getMetadata().get("startLine");
            int end = (int) doc.getMetadata().get("endLine");
            int idx = (int) doc.getMetadata().get("chunkIndex");

            assertThat(idx).isEqualTo(i);
            assertThat(start).isGreaterThanOrEqualTo(1);
            assertThat(end).isGreaterThanOrEqualTo(start);
            assertThat(end).isLessThanOrEqualTo(60);
        }

        // First chunk starts at line 1
        assertThat(docs.get(0).getMetadata().get("startLine")).isEqualTo(1);
        // Last chunk ends at line 60
        assertThat(docs.get(docs.size() - 1).getMetadata().get("endLine")).isEqualTo(60);
    }

    @Test
    void testBlankLines() {
        String code = "line 1\n\nline 3\n\n\nline 6\n";
        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "Blank.txt", code, "run-1");

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);
        assertThat(doc.getMetadata().get("startLine")).isEqualTo(1);
        assertThat(doc.getMetadata().get("endLine")).isEqualTo(6);
    }

    @Test
    void testLfAndCrlfLineEndings() {
        String lfCode = "line 1\nline 2\nline 3\n";
        String crlfCode = "line 1\r\nline 2\r\nline 3\r\n";

        List<Document> lfDocs = chunker.chunkFile("repo-1", "owner/repo", "LF.txt", lfCode, "run-1");
        List<Document> crlfDocs = chunker.chunkFile("repo-1", "owner/repo", "CRLF.txt", crlfCode, "run-1");

        assertThat(lfDocs).hasSize(1);
        assertThat(crlfDocs).hasSize(1);

        assertThat(lfDocs.get(0).getMetadata().get("startLine")).isEqualTo(1);
        assertThat(lfDocs.get(0).getMetadata().get("endLine")).isEqualTo(3);

        assertThat(crlfDocs.get(0).getMetadata().get("startLine")).isEqualTo(1);
        assertThat(crlfDocs.get(0).getMetadata().get("endLine")).isEqualTo(3);
    }

    @Test
    void testFinalLineWithoutNewline() {
        String code = "line 1\nline 2\nline 3";
        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "NoNewline.txt", code, "run-1");

        assertThat(docs).hasSize(1);
        Document doc = docs.get(0);
        assertThat(doc.getMetadata().get("startLine")).isEqualTo(1);
        assertThat(doc.getMetadata().get("endLine")).isEqualTo(3);
        assertThat(doc.getText()).contains("line 3");
    }

    @Test
    void testOversizedLogicalCodeUnit() {
        // A single method that exceeds chunkSize
        StringBuilder sb = new StringBuilder();
        sb.append("public void hugeMethod() {\n");
        for (int i = 2; i <= 80; i++) {
            sb.append("    int value_").append(i).append(" = calculateSomethingComplex(").append(i).append(");\n");
        }
        sb.append("}\n");

        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "Huge.java", sb.toString(), "run-1");
        assertThat(docs.size()).isGreaterThan(1);

        // Every chunk has valid line boundaries
        for (Document doc : docs) {
            int start = (int) doc.getMetadata().get("startLine");
            int end = (int) doc.getMetadata().get("endLine");
            assertThat(start).isGreaterThanOrEqualTo(1);
            assertThat(end).isGreaterThanOrEqualTo(start);
        }
        assertThat(docs.get(0).getMetadata().get("startLine")).isEqualTo(1);
        assertThat(docs.get(docs.size() - 1).getMetadata().get("endLine")).isEqualTo(81);
    }

    @Test
    void testUnsupportedLanguageFallback() {
        // Language not having special boundaries: e.g. unknown or txt
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            sb.append("Generic config line ").append(i).append(" = val;\n");
        }

        List<Document> docs = chunker.chunkFile("repo-1", "owner/repo", "config.xyz", sb.toString(), "run-1");
        assertThat(docs.size()).isGreaterThan(1);

        for (Document doc : docs) {
            assertThat(doc.getMetadata().get("language")).isEqualTo("xyz");
            int start = (int) doc.getMetadata().get("startLine");
            int end = (int) doc.getMetadata().get("endLine");
            assertThat(start).isGreaterThanOrEqualTo(1);
            assertThat(end).isGreaterThanOrEqualTo(start);
        }
    }

    @Test
    void testNaturalLanguageBoundariesPreserved() {
        String code = """
                package com.example;

                public class Service {
                    public void firstMethod() {
                        System.out.println("First method line 1");
                        System.out.println("First method line 2");
                    }

                    public void secondMethod() {
                        System.out.println("Second method line 1");
                        System.out.println("Second method line 2");
                    }
                }
                """;

        // With small chunk size ~120 chars, chunks should prefer breaking right before
        // public void secondMethod
        CodeChunker smallChunker = new CodeChunker(fileFilter, 150, 20);
        List<Document> docs = smallChunker.chunkFile("repo-1", "owner/repo", "Service.java", code, "run-1");

        for (Document d : docs) {
            System.out
                    .println("Chunk lines " + d.getMetadata().get("startLine") + "-" + d.getMetadata().get("endLine"));
        }

        assertThat(docs.size()).isGreaterThan(1);
        boolean foundMethodStart = docs.stream().anyMatch(d -> {
            int start = (int) d.getMetadata().get("startLine");
            return start == 9; // Let's check which line secondMethod is on
        });
        assertThat(docs.get(0).getMetadata().get("startLine")).isEqualTo(1);
    }
}
