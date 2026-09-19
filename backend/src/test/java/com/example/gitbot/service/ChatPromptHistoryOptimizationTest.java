package com.example.gitbot.service;

import com.example.gitbot.dto.RetrievedContextDto;
import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.service.ai.ChatPromptBuilder;
import com.example.gitbot.service.ai.ChatStreamHandler;
import com.example.gitbot.service.ai.CodeContextRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPromptHistoryOptimizationTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private GitRepoService gitRepoService;
    @Mock
    private CodeContextRetriever codeContextRetriever;
    @Mock
    private ChatPromptBuilder chatPromptBuilder;
    @Mock
    private ChatStreamHandler chatStreamHandler;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID sessionId;
    private UUID repoId;
    private ChatSession session;
    private GitRepo repo;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        repoId = UUID.randomUUID();

        session = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .repositoryId(repoId)
                .title("Test Chat")
                .build();

        repo = GitRepo.builder()
                .id(repoId)
                .userId(userId)
                .fullName("owner/repo")
                .indexStatus(IndexStatus.READY)
                .build();
    }

    @Test
    @DisplayName("streamReply queries only latest 10 messages from DB instead of full conversation scan")
    void streamReply_queriesOnlyLatest10FromDb() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(gitRepoService.requireOwned(repoId, userId)).thenReturn(repo);

        Instant base = Instant.parse("2026-09-19T14:00:00Z");
        List<ChatMessage> recent10Desc = new ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            recent10Desc.add(ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .role(i % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER)
                    .content("Msg " + i)
                    .createdAt(base.plusSeconds(i * 5))
                    .build());
        }

        // Must request PageRequest.of(0, 10)
        when(chatMessageRepository.findLatestMessages(eq(sessionId), eq(PageRequest.of(0, ChatPromptBuilder.MAX_HISTORY_MESSAGES))))
                .thenReturn(recent10Desc);

        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        when(codeContextRetriever.retrieve(eq(repoId), eq("New question")))
                .thenReturn(new RetrievedContextDto(List.of(), "context snippet"));

        when(chatStreamHandler.stream(any(), any(), any(), any(), any()))
                .thenReturn(new SseEmitter());

        chatService.streamReply(userId, sessionId, "New question");

        // Verify that findLatestMessages was called with limit 10
        verify(chatMessageRepository).findLatestMessages(sessionId, PageRequest.of(0, 10));

        // Verify unbounded findBySessionIdOrderByCreatedAtAsc was NEVER called
        verify(chatMessageRepository, never()).findBySessionIdOrderByCreatedAtAsc(sessionId);

        // Verify chatPromptBuilder receives messages in chronological order (Msg 1 to Msg 10)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> historyCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatPromptBuilder).buildMessages(eq(repo.getFullName()), historyCaptor.capture(), eq("context snippet"), eq("New question"));

        List<ChatMessage> passedHistory = historyCaptor.getValue();
        assertThat(passedHistory).hasSize(10);
        assertThat(passedHistory.get(0).getContent()).isEqualTo("Msg 1");
        assertThat(passedHistory.get(9).getContent()).isEqualTo("Msg 10");
    }
}
