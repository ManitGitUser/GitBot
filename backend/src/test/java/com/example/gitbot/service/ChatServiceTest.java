package com.example.gitbot.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.gitbot.dto.ChatMessageResponse;
import com.example.gitbot.dto.ChatSessionResponse;
import com.example.gitbot.dto.PublicSharedChatResponse;
import com.example.gitbot.dto.RetrievedContextDto;
import com.example.gitbot.dto.ShareResponse;
import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.MessageReport;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;
import com.example.gitbot.enums.ReportReason;
import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.repository.MessageReportRepository;
import com.example.gitbot.service.ai.ChatPromptBuilder;
import com.example.gitbot.service.ai.ChatStreamHandler;
import com.example.gitbot.service.ai.CitationMapper;
import com.example.gitbot.service.ai.CodeContextRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MessageReportRepository messageReportRepository;

    @Mock
    private GitRepoService gitRepoService;

    @Mock
    private CodeContextRetriever codeContextRetriever;

    @Mock
    private ChatPromptBuilder chatPromptBuilder;

    @Mock
    private ChatStreamHandler chatStreamHandler;

    @Mock
    private CitationMapper citationMapper;

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
                .createdAt(Instant.now())
                .build();

        repo = GitRepo.builder()
                .id(repoId)
                .userId(userId)
                .fullName("owner/test-repo")
                .indexStatus(IndexStatus.READY)
                .build();
    }

    @Test
    void requireSession_throwsNotFound_whenOwnershipFails() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.requireSession(userId, sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Chat session not found");
    }

    @Test
    void deleteSession_deletesMessagesReportsAndSession() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        chatService.deleteSession(userId, sessionId);

        verify(chatStreamHandler).stopStream(sessionId);
        verify(messageReportRepository).deleteBySessionId(sessionId);
        verify(chatMessageRepository).deleteBySessionId(sessionId);
        verify(chatSessionRepository).delete(session);
    }

    @Test
    void renameSession_validatesAndTrimsTitle() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatSessionResponse res = chatService.renameSession(userId, sessionId, "  New Refined Title  ");
        assertThat(res.title()).isEqualTo("New Refined Title");

        assertThatThrownBy(() -> chatService.renameSession(userId, sessionId, "   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("blank");

        String longTitle = "a".repeat(201);
        assertThatThrownBy(() -> chatService.renameSession(userId, sessionId, longTitle))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("200 characters");
    }

    @Test
    void branchSession_copiesHistoryUpToCutoff() {
        UUID cutoffId = UUID.randomUUID();
        Instant cutoffTime = Instant.now().minusSeconds(10);
        ChatMessage cutoffMessage = ChatMessage.builder()
                .id(cutoffId)
                .sessionId(sessionId)
                .role(MessageRole.ASSISTANT)
                .content("Answer 1")
                .createdAt(cutoffTime)
                .build();

        ChatMessage m1 = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.USER).content("Q1").createdAt(cutoffTime.minusSeconds(5)).build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByIdAndSessionId(cutoffId, sessionId)).thenReturn(Optional.of(cutoffMessage));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(chatMessageRepository.findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(sessionId, cutoffTime))
                .thenReturn(List.of(m1, cutoffMessage));

        ChatSessionResponse branchRes = chatService.branchSession(userId, sessionId, cutoffId, "Exploration Branch");

        assertThat(branchRes.title()).isEqualTo("Exploration Branch");
        assertThat(branchRes.parentSessionId()).isEqualTo(sessionId);
        assertThat(branchRes.branchMessageId()).isEqualTo(cutoffId);

        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void createShare_generatesTokenAndUrl() {
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareResponse share = chatService.createShare(userId, sessionId);

        assertThat(share.shareToken()).isNotNull().hasSize(64);
        assertThat(share.shareUrl()).isEqualTo("/share/" + share.shareToken());
        assertThat(session.isShared()).isTrue();
        assertThat(session.getSharedAt()).isNotNull();
    }

    @Test
    void revokeShare_clearsTokenAndSharedFlag() {
        session.setShared(true);
        session.setShareToken("some-token-64-characters-long-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        session.setSharedAt(Instant.now());

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatService.revokeShare(userId, sessionId);

        assertThat(session.isShared()).isFalse();
        assertThat(session.getShareToken()).isNull();
        assertThat(session.getSharedAt()).isNull();
    }

    @Test
    void getPublicShare_returnsSharedMessages_whenTokenValid() {
        String token = "valid-share-token-64-chars-abcdefghijklmnopqrstuvwxyz0123456789";
        session.setShared(true);
        session.setShareToken(token);
        session.setSharedAt(Instant.now());

        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .role(MessageRole.ASSISTANT)
                .status(MessageStatus.COMPLETE)
                .content("Public answer")
                .createdAt(Instant.now())
                .build();

        when(chatSessionRepository.findByShareTokenAndIsSharedTrue(token)).thenReturn(Optional.of(session));
        when(gitRepoService.getById(repoId)).thenReturn(repo);
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(List.of(msg));
        when(chatStreamHandler.toMessageResponse(msg)).thenReturn(new ChatMessageResponse(
                msg.getId(), msg.getRole(), msg.getStatus(), msg.getContent(), List.of(), msg.getCreatedAt()
        ));

        PublicSharedChatResponse response = chatService.getPublicShare(token);

        assertThat(response.title()).isEqualTo("Test Chat");
        assertThat(response.repoFullName()).isEqualTo("owner/test-repo");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).content()).isEqualTo("Public answer");
    }

    @Test
    void getPublicShare_throwsNotFound_whenShareRevokedOrNotFound() {
        when(chatSessionRepository.findByShareTokenAndIsSharedTrue("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getPublicShare("invalid-token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Shared chat not found");
    }

    @Test
    void reportMessage_savesReport_whenValid() {
        UUID messageId = UUID.randomUUID();
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(messageId)
                .sessionId(sessionId)
                .role(MessageRole.ASSISTANT)
                .content("Hallucinated answer")
                .build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByIdAndSessionId(messageId, sessionId)).thenReturn(Optional.of(assistantMsg));
        when(messageReportRepository.existsByUserIdAndMessageId(userId, messageId)).thenReturn(false);

        chatService.reportMessage(userId, sessionId, messageId, ReportReason.INCORRECT, "Factually inaccurate");

        ArgumentCaptor<MessageReport> captor = ArgumentCaptor.forClass(MessageReport.class);
        verify(messageReportRepository).save(captor.capture());

        MessageReport report = captor.getValue();
        assertThat(report.getMessageId()).isEqualTo(messageId);
        assertThat(report.getUserId()).isEqualTo(userId);
        assertThat(report.getSessionId()).isEqualTo(sessionId);
        assertThat(report.getReason()).isEqualTo(ReportReason.INCORRECT);
        assertThat(report.getDetails()).isEqualTo("Factually inaccurate");
    }

    @Test
    void reportMessage_rejectsDuplicateReport() {
        UUID messageId = UUID.randomUUID();
        ChatMessage assistantMsg = ChatMessage.builder()
                .id(messageId)
                .sessionId(sessionId)
                .role(MessageRole.ASSISTANT)
                .content("Answer")
                .build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByIdAndSessionId(messageId, sessionId)).thenReturn(Optional.of(assistantMsg));
        when(messageReportRepository.existsByUserIdAndMessageId(userId, messageId)).thenReturn(true);

        assertThatThrownBy(() -> chatService.reportMessage(userId, sessionId, messageId, ReportReason.OTHER, "Dupe"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already reported");
    }

    @Test
    void reportMessage_rejectsUserMessageReport() {
        UUID messageId = UUID.randomUUID();
        ChatMessage userMsg = ChatMessage.builder()
                .id(messageId)
                .sessionId(sessionId)
                .role(MessageRole.USER)
                .content("Question")
                .build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findByIdAndSessionId(messageId, sessionId)).thenReturn(Optional.of(userMsg));

        assertThatThrownBy(() -> chatService.reportMessage(userId, sessionId, messageId, ReportReason.OTHER, "Error"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only assistant messages can be reported");
    }

    @Test
    void streamRetry_identifiesTargetTurnAndPassesPriorHistory() {
        Instant t0 = Instant.now().minusSeconds(20);
        Instant t1 = Instant.now().minusSeconds(15);
        Instant t2 = Instant.now().minusSeconds(10);
        Instant t3 = Instant.now().minusSeconds(5);

        ChatMessage priorUser = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.USER).content("Prior Q").createdAt(t0).build();
        ChatMessage priorAssistant = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.ASSISTANT).content("Prior A").createdAt(t1).build();
        ChatMessage targetUser = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.USER).content("Target Q").createdAt(t2).build();
        ChatMessage targetAssistant = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.ASSISTANT).status(MessageStatus.FAILED).content("Failed A").createdAt(t3).build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(gitRepoService.requireOwned(repoId, userId)).thenReturn(repo);
        when(chatMessageRepository.findByIdAndSessionId(targetAssistant.getId(), sessionId)).thenReturn(Optional.of(targetAssistant));
        when(chatMessageRepository.findMessagesBefore(eq(sessionId), eq(targetAssistant.getCreatedAt()), eq(targetAssistant.getId()), eq(org.springframework.data.domain.PageRequest.of(0, 1))))
                .thenReturn(List.of(targetUser));
        when(chatMessageRepository.findMessagesBefore(eq(sessionId), eq(targetUser.getCreatedAt()), eq(targetUser.getId()), eq(org.springframework.data.domain.PageRequest.of(0, ChatPromptBuilder.MAX_HISTORY_MESSAGES))))
                .thenReturn(List.of(priorAssistant, priorUser));
        when(codeContextRetriever.retrieve(repoId, "Target Q")).thenReturn(new RetrievedContextDto(List.of(), "code snippet"));
        when(chatPromptBuilder.buildMessages(eq(repo.getFullName()), any(), eq("code snippet"), eq("Target Q"))).thenReturn(List.of());
        when(chatStreamHandler.stream(eq(sessionId), any(), any(), any(), eq(targetAssistant.getId()))).thenReturn(new SseEmitter());

        SseEmitter emitter = chatService.streamRetry(userId, sessionId, targetAssistant.getId());
        assertThat(emitter).isNotNull();

        verify(chatStreamHandler).stream(eq(sessionId), any(), any(), any(), eq(targetAssistant.getId()));
    }

    @Test
    void streamRetry_whenRetryingUserMessage_usesBoundedFindMessagesAfter() {
        Instant t0 = Instant.now().minusSeconds(10);
        Instant t1 = Instant.now().minusSeconds(5);

        ChatMessage userMsg = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.USER).content("User Q").createdAt(t0).build();
        ChatMessage nextAssistant = ChatMessage.builder().id(UUID.randomUUID()).sessionId(sessionId).role(MessageRole.ASSISTANT).status(MessageStatus.COMPLETE).content("Old A").createdAt(t1).build();

        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(gitRepoService.requireOwned(repoId, userId)).thenReturn(repo);
        when(chatMessageRepository.findByIdAndSessionId(userMsg.getId(), sessionId)).thenReturn(Optional.of(userMsg));
        when(chatMessageRepository.findMessagesAfter(eq(sessionId), eq(userMsg.getCreatedAt()), eq(userMsg.getId()), eq(org.springframework.data.domain.PageRequest.of(0, 1))))
                .thenReturn(List.of(nextAssistant));
        when(chatMessageRepository.findMessagesBefore(eq(sessionId), eq(userMsg.getCreatedAt()), eq(userMsg.getId()), eq(org.springframework.data.domain.PageRequest.of(0, ChatPromptBuilder.MAX_HISTORY_MESSAGES))))
                .thenReturn(List.of());
        when(codeContextRetriever.retrieve(repoId, "User Q")).thenReturn(new RetrievedContextDto(List.of(), "code snippet"));
        when(chatPromptBuilder.buildMessages(eq(repo.getFullName()), any(), eq("code snippet"), eq("User Q"))).thenReturn(List.of());
        when(chatStreamHandler.stream(eq(sessionId), any(), any(), any(), eq(nextAssistant.getId()))).thenReturn(new SseEmitter());

        SseEmitter emitter = chatService.streamRetry(userId, sessionId, userMsg.getId());
        assertThat(emitter).isNotNull();

        verify(chatMessageRepository).findMessagesAfter(eq(sessionId), eq(userMsg.getCreatedAt()), eq(userMsg.getId()), eq(org.springframework.data.domain.PageRequest.of(0, 1)));
        verify(chatMessageRepository, never()).findBySessionIdOrderByCreatedAtAsc(any());
        verify(chatStreamHandler).stream(eq(sessionId), any(), any(), any(), eq(nextAssistant.getId()));
    }
}
