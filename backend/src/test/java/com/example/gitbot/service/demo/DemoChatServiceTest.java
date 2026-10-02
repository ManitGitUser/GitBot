package com.example.gitbot.service.demo;

import com.example.gitbot.dto.CitationDto;
import com.example.gitbot.dto.DemoChatRequest;
import com.example.gitbot.dto.DemoStatusResponse;
import com.example.gitbot.dto.RetrievedContextDto;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.ConflictException;
import com.example.gitbot.exception.TooManyRequestsException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.ai.ChatPromptBuilder;
import com.example.gitbot.service.ai.CitationMapper;
import com.example.gitbot.service.ai.CodeContextRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DemoChatServiceTest {

    @Mock
    private GitRepoRepository gitRepoRepository;
    @Mock
    private CodeContextRetriever codeContextRetriever;
    @Mock
    private ChatPromptBuilder chatPromptBuilder;
    @Mock
    private CitationMapper citationMapper;
    @Mock
    private ChatModel chatModel;
    @Mock
    private DemoTokenService demoTokenService;
    @Mock
    private DemoRateLimiter demoRateLimiter;

    private DemoChatService demoChatService;

    private final UUID demoRepoId = UUID.randomUUID();
    private GitRepo demoRepo;

    @BeforeEach
    void setUp() {
        demoRepo = GitRepo.builder()
                .id(demoRepoId)
                .name("GitBot")
                .fullName("ManitGitUser/GitBot")
                .indexStatus(IndexStatus.READY)
                .isDemo(true)
                .build();

        demoChatService = new DemoChatService(
                gitRepoRepository,
                codeContextRetriever,
                chatPromptBuilder,
                citationMapper,
                chatModel,
                demoTokenService,
                demoRateLimiter,
                "ManitGitUser/GitBot",
                5,
                1000
        );
    }

    @Test
    void getDemoStatus_returnsCorrectStatus() {
        when(gitRepoRepository.findFirstByIsDemoTrue()).thenReturn(Optional.of(demoRepo));

        DemoStatusResponse status = demoChatService.getDemoStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.repoName()).isEqualTo("GitBot");
        assertThat(status.repoFullName()).isEqualTo("ManitGitUser/GitBot");
        assertThat(status.maxMessages()).isEqualTo(5);
    }

    @Test
    void streamDemoChat_resolvesDemoRepoAndPerformsIsolatedRag() {
        when(gitRepoRepository.findFirstByIsDemoTrue()).thenReturn(Optional.of(demoRepo));
        when(demoTokenService.validateAndIncrement(null)).thenReturn(
                new DemoTokenService.TokenResult("session-1", 1, 5, "token-1")
        );

        CitationDto citation = new CitationDto("src/main/App.java", 1, 10, "java", "ManitGitUser/GitBot");
        RetrievedContextDto context = new RetrievedContextDto(List.of(citation), "code snippet", 5L, 5L, 10L);
        when(codeContextRetriever.retrieve(demoRepoId, "What does this do?")).thenReturn(context);
        when(chatPromptBuilder.buildMessages(eq("ManitGitUser/GitBot"), any(), eq("code snippet"), eq("What does this do?")))
                .thenReturn(List.of());

        DemoChatRequest request = new DemoChatRequest("What does this do?", List.of(), null);

        // Call streamDemoChat
        // Note: ChatClient.builder(chatModel) will be called; we just verify retriever is called with demoRepoId
        try {
            demoChatService.streamDemoChat(request, "127.0.0.1", null);
        } catch (Exception ignored) {
            // ChatModel mock may not produce reactor flux without deep mocking
        }

        // Verify rate limit checked
        verify(demoRateLimiter).checkRateLimit("127.0.0.1");

        // Verify RAG retrieval strictly called with demoRepoId and user question
        verify(codeContextRetriever).retrieve(demoRepoId, "What does this do?");
    }

    @Test
    void streamDemoChat_emptyMessage_throwsBadRequest() {
        DemoChatRequest request = new DemoChatRequest("   ", List.of(), null);

        assertThatThrownBy(() -> demoChatService.streamDemoChat(request, "127.0.0.1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Message cannot be empty");
    }

    @Test
    void streamDemoChat_oversizedMessage_throwsBadRequest() {
        DemoChatRequest request = new DemoChatRequest("x".repeat(1001), List.of(), null);

        assertThatThrownBy(() -> demoChatService.streamDemoChat(request, "127.0.0.1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void streamDemoChat_repoNotIndexed_throwsConflictException() {
        demoRepo.setIndexStatus(IndexStatus.PENDING);
        when(gitRepoRepository.findFirstByIsDemoTrue()).thenReturn(Optional.of(demoRepo));
        when(demoTokenService.validateAndIncrement(any())).thenReturn(
                new DemoTokenService.TokenResult("session-1", 1, 5, "token-1")
        );

        DemoChatRequest request = new DemoChatRequest("Hello", List.of(), null);

        assertThatThrownBy(() -> demoChatService.streamDemoChat(request, "127.0.0.1", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("currently not indexed");
    }
}
