package com.example.gitbot.service;

import com.example.gitbot.dto.ChatSessionResponse;
import com.example.gitbot.dto.PageResponse;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
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
class ChatSessionPaginationTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private GitRepoService gitRepoService;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID repoId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Page 0 returns requested batch of 10 chat sessions ordered by createdAt DESC")
    void page0_returns10Sessions_withMetadata() {
        when(gitRepoService.requireOwned(repoId, userId)).thenReturn(GitRepo.builder().id(repoId).userId(userId).build());

        List<ChatSession> sessions = new ArrayList<>();
        Instant base = Instant.parse("2026-09-19T12:00:00Z");
        for (int i = 0; i < 10; i++) {
            sessions.add(ChatSession.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .repositoryId(repoId)
                    .title("Chat " + i)
                    .createdAt(base.minusSeconds(i * 60))
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 10);
        when(chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(eq(userId), eq(repoId), eq(pageable)))
                .thenReturn(new PageImpl<>(sessions, pageable, 25));

        PageResponse<ChatSessionResponse> result = chatService.listSessions(userId, repoId, pageable);

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();

        verify(chatSessionRepository).findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repoId, pageable);
    }

    @Test
    @DisplayName("Page 1 returns next page with correct hasPrevious and hasNext")
    void page1_returnsNextPage() {
        when(gitRepoService.requireOwned(repoId, userId)).thenReturn(GitRepo.builder().id(repoId).userId(userId).build());

        List<ChatSession> sessions = new ArrayList<>();
        Instant base = Instant.parse("2026-09-19T11:00:00Z");
        for (int i = 10; i < 20; i++) {
            sessions.add(ChatSession.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .repositoryId(repoId)
                    .title("Chat " + i)
                    .createdAt(base.minusSeconds(i * 60))
                    .build());
        }

        Pageable pageable = PageRequest.of(1, 10);
        when(chatSessionRepository.findByUserIdAndRepositoryIdOrderByCreatedAtDesc(eq(userId), eq(repoId), eq(pageable)))
                .thenReturn(new PageImpl<>(sessions, pageable, 25));

        PageResponse<ChatSessionResponse> result = chatService.listSessions(userId, repoId, pageable);

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }
}
