package com.example.gitbot.service.ai;

import com.example.gitbot.dto.RetrievedContextDto;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CodeContextRetriever {

    private static final String NO_MATCHES = "( unfortunately no matching code chunks found, try something else buddy )";

    private final VectorStore vectorStore;
    private final CitationMapper citationMapper;

    public RetrievedContextDto retrieve(UUID repositoryId, String question) {
        var filter = new FilterExpressionBuilder()
                .eq(RagSettings.METADATA_REPO_ID, repositoryId.toString())
                .build();

        var search = SearchRequest.builder()
                .query(question)
                .topK(RagSettings.TOP_K_CHUNKS)
                .filterExpression(filter)
                .build();

        var documents = vectorStore.similaritySearch(search);

        var citations = documents.stream()
                .map(citationMapper::fromDocument)
                .distinct()
                .toList();

        var contextText = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        if (contextText.isBlank()) {
            contextText = NO_MATCHES;
        }

        return new RetrievedContextDto(citations, contextText);
    }
}
