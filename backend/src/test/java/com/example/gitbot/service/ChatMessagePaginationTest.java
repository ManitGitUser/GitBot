package com.example.gitbot.service;

import com.example.gitbot.dto.PagedMessagesResponse;
import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessagePaginationTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private com.example.gitbot.service.ai.ChatStreamHandler chatStreamHandler;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID sessionId;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        session = ChatSession.builder()
                .id(sessionId)
                .userId(userId)
                .repositoryId(UUID.randomUUID())
                .title("Test Chat")
                .build();

        org.mockito.Mockito.lenient().when(chatStreamHandler.toMessageResponse(any()))
                .thenAnswer(inv -> {
                    ChatMessage m = inv.getArgument(0);
                    return new com.example.gitbot.dto.ChatMessageResponse(
                            m.getId(), m.getRole(), m.getStatus(), m.getContent(), List.of(), m.getCreatedAt()
                    );
                });
    }

    @Test
    @DisplayName("Initial load returns latest 10 messages in chronological ASC order with hasMore and nextCursor")
    void initialLoad_returnsLatest10_chronologicalAsc() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        Instant base = Instant.parse("2026-09-19T10:00:00Z");
        // Simulated DB result for findLatestMessages (ordered DESC, returning 11 items for limit 10)
        List<ChatMessage> dbDesc = new ArrayList<>();
        for (int i = 11; i >= 1; i--) {
            dbDesc.add(ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .sessionId(sessionId)
                    .role(i % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER)
                    .content("Message " + i)
                    .createdAt(base.plusSeconds(i * 10))
                    .build());
        }

        when(chatMessageRepository.findLatestMessages(eq(sessionId), eq(PageRequest.of(0, 11))))
                .thenReturn(dbDesc);

        PagedMessagesResponse response = chatService.getMessages(userId, sessionId, null, 10);

        assertThat(response.messages()).hasSize(10);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNotNull();

        // Chronological order: oldest of the 10 first, newest of the 10 last
        assertThat(response.messages().get(0).content()).isEqualTo("Message 2");
        assertThat(response.messages().get(9).content()).isEqualTo("Message 11");

        ChatMessage oldestInPage = dbDesc.get(9); // Message 2
        String expectedCursor = oldestInPage.getCreatedAt().toString() + "_" + oldestInPage.getId().toString();
        assertThat(response.nextCursor()).isEqualTo(expectedCursor);
    }

    @Test
    @DisplayName("Load earlier messages with cursor returns previous 10 messages")
    void loadEarlier_withCursor_returnsPreviousBatch() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        Instant cursorTime = Instant.parse("2026-09-19T10:00:20Z");
        UUID cursorId = UUID.randomUUID();
        String cursor = cursorTime.toString() + "_" + cursorId.toString();

        // Only 1 message remains before this cursor
        ChatMessage oldest = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(MessageRole.USER)
                .content("Message 1")
                .createdAt(cursorTime.minusSeconds(10))
                .build();

        when(chatMessageRepository.findMessagesBefore(eq(sessionId), eq(cursorTime), eq(cursorId), eq(PageRequest.of(0, 11))))
                .thenReturn(List.of(oldest));

        PagedMessagesResponse response = chatService.getMessages(userId, sessionId, cursor, 10);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).content()).isEqualTo("Message 1");
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("Empty conversation returns empty list, hasMore=false, nextCursor=null")
    void emptyConversation_returnsEmpty() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findLatestMessages(eq(sessionId), any()))
                .thenReturn(Collections.emptyList());

        PagedMessagesResponse response = chatService.getMessages(userId, sessionId, null, 10);

        assertThat(response.messages()).isEmpty();
        assertThat(response.hasMore()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("Non-owner cannot access messages from another user's session")
    void nonOwner_rejected() {
        UUID nonOwner = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndUserId(sessionId, nonOwner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(nonOwner, sessionId, null, 10))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Chat session not found");
    }
}
