package com.example.gitbot.service;

import com.example.gitbot.dto.ChatSessionResponse;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRecentSessionsTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private GitRepoService gitRepoService;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID repo1Id;
    private UUID repo2Id;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repo1Id = UUID.randomUUID();
        repo2Id = UUID.randomUUID();
    }

    @Test
    @DisplayName("Returns at most 10 conversations and passes limit 10 in Pageable")
    void listRecentSessions_limitsTo10() {
        List<ChatSession> sessions = new ArrayList<>();
        Instant base = Instant.parse("2026-09-20T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            sessions.add(ChatSession.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .repositoryId(i % 2 == 0 ? repo1Id : repo2Id)
                    .title("Chat " + i)
                    .createdAt(base.minusSeconds(i * 60))
                    .build());
        }

        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10))))
                .thenReturn(sessions);

        List<ChatSessionResponse> result = chatService.listRecentSessions(userId, 10);

        assertThat(result).hasSize(10);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatSessionRepository).findByUserIdOrderByCreatedAtDesc(eq(userId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("Orders conversations correctly with most recent first")
    void listRecentSessions_orderedByCreatedAtDesc() {
        Instant t1 = Instant.parse("2026-09-20T01:00:00Z");
        Instant t2 = Instant.parse("2026-09-20T00:50:00Z");
        Instant t3 = Instant.parse("2026-09-20T00:40:00Z");

        ChatSession s1 = ChatSession.builder().id(UUID.randomUUID()).userId(userId).repositoryId(repo1Id).title("Newest").createdAt(t1).build();
        ChatSession s2 = ChatSession.builder().id(UUID.randomUUID()).userId(userId).repositoryId(repo2Id).title("Middle").createdAt(t2).build();
        ChatSession s3 = ChatSession.builder().id(UUID.randomUUID()).userId(userId).repositoryId(repo1Id).title("Oldest").createdAt(t3).build();

        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(s1, s2, s3));

        List<ChatSessionResponse> result = chatService.listRecentSessions(userId, 10);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).title()).isEqualTo("Newest");
        assertThat(result.get(1).title()).isEqualTo("Middle");
        assertThat(result.get(2).title()).isEqualTo("Oldest");
        assertThat(result.get(0).createdAt()).isAfter(result.get(1).createdAt());
        assertThat(result.get(1).createdAt()).isAfter(result.get(2).createdAt());
    }

    @Test
    @DisplayName("Only queries and returns conversations for the authenticated user")
    void listRecentSessions_userIsolation() {
        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());

        List<ChatSessionResponse> result = chatService.listRecentSessions(userId, 10);

        assertThat(result).isEmpty();
        verify(chatSessionRepository).findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10)));
    }

    @Test
    @DisplayName("Conversations from multiple repositories appear together in the recent list")
    void listRecentSessions_multipleRepositoriesTogether() {
        ChatSession repo1Session = ChatSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .repositoryId(repo1Id)
                .title("Repo 1 Chat")
                .createdAt(Instant.now().minusSeconds(10))
                .build();

        ChatSession repo2Session = ChatSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .repositoryId(repo2Id)
                .title("Repo 2 Chat")
                .createdAt(Instant.now().minusSeconds(20))
                .build();

        when(chatSessionRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(repo1Session, repo2Session));

        List<ChatSessionResponse> result = chatService.listRecentSessions(userId, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).repositoryId()).isEqualTo(repo1Id);
        assertThat(result.get(1).repositoryId()).isEqualTo(repo2Id);
        assertThat(result).extracting(ChatSessionResponse::repositoryId)
                .containsExactlyInAnyOrder(repo1Id, repo2Id);
    }
}
