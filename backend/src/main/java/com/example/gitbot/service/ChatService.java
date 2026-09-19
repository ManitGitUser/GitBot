package com.example.gitbot.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.gitbot.dto.ChatMessageResponse;
import com.example.gitbot.dto.ChatSessionResponse;
import com.example.gitbot.dto.CreateChatSessionRequest;
import com.example.gitbot.dto.PublicSharedChatResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Chat sessions, branching, sharing, reporting, and RAG chat streaming
 * pipeline.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageReportRepository messageReportRepository;
    private final GitRepoService gitRepoService;
    private final CodeContextRetriever codeContextRetriever;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatStreamHandler chatStreamHandler;
    private final CitationMapper citationMapper;

    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        GitRepo repo = gitRepoService.requireOwned(request.repositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository must be indexed before chatting");
        }

        String title = request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : "Chat with " + repo.getFullName();

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .repositoryId(repo.getId())
                .title(title)
                .build();
        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        gitRepoService.requireOwned(repositoryId, userId);
        return chatSessionRepository
                .findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSession requireSession(UUID userId, UUID sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));
    }

    public SseEmitter streamReply(UUID userId, UUID sessionId, String userContent) {
        ChatSession session = requireSession(userId, sessionId);
        GitRepo repo = gitRepoService.requireOwned(session.getRepositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository is not ready for chat");
        }

        // Cancel any existing active stream and persist interrupted state before reading history
        chatStreamHandler.stopStream(sessionId);

        // 1. Fetch prior conversation history before persisting the current question
        List<ChatMessage> priorMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        // 2. Persist the user's message
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .status(MessageStatus.COMPLETE)
                .content(userContent.trim())
                .build());

        // 3. RAG retrieval — find code chunks similar to the question
        var retrievedContext = codeContextRetriever.retrieve(repo.getId(), userContent.trim());

        // 4. Build LLM messages including history + code context + question
        List<Message> promptMessages = chatPromptBuilder.buildMessages(
                repo.getFullName(),
                priorMessages,
                retrievedContext.contextText(),
                userContent.trim());

        // 5. Stream LLM response
        return chatStreamHandler.stream(
                session.getId(),
                toMessageResponse(userMessage),
                retrievedContext.citations(),
                promptMessages,
                null);
    }

    public SseEmitter streamRetry(UUID userId, UUID sessionId, UUID messageId) {
        ChatSession session = requireSession(userId, sessionId);
        GitRepo repo = gitRepoService.requireOwned(session.getRepositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository is not ready for chat");
        }

        // Cancel any existing active stream and persist interrupted state before reading messages
        chatStreamHandler.stopStream(sessionId);

        List<ChatMessage> allMessages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        int targetIdx = -1;
        for (int i = 0; i < allMessages.size(); i++) {
            if (allMessages.get(i).getId().equals(messageId)) {
                targetIdx = i;
                break;
            }
        }

        if (targetIdx == -1) {
            throw new NotFoundException("Message not found in this session");
        }

        ChatMessage targetMsg = allMessages.get(targetIdx);
        ChatMessage userMsg;
        ChatMessage assistantMsg = null;

        if (targetMsg.getRole() == MessageRole.ASSISTANT) {
            assistantMsg = targetMsg;
            if (targetIdx == 0 || allMessages.get(targetIdx - 1).getRole() != MessageRole.USER) {
                throw new BadRequestException("Cannot find corresponding user message to retry");
            }
            userMsg = allMessages.get(targetIdx - 1);
        } else {
            userMsg = targetMsg;
            if (targetIdx + 1 < allMessages.size()
                    && allMessages.get(targetIdx + 1).getRole() == MessageRole.ASSISTANT) {
                assistantMsg = allMessages.get(targetIdx + 1);
            }
        }

        // History turns strictly before this user question
        List<ChatMessage> historyMessages = new ArrayList<>();
        for (ChatMessage m : allMessages) {
            if (m.getCreatedAt().isBefore(userMsg.getCreatedAt())) {
                historyMessages.add(m);
            }
        }

        var retrievedContext = codeContextRetriever.retrieve(repo.getId(), userMsg.getContent());
        List<Message> promptMessages = chatPromptBuilder.buildMessages(
                repo.getFullName(),
                historyMessages,
                retrievedContext.contextText(),
                userMsg.getContent());

        return chatStreamHandler.stream(
                session.getId(),
                toMessageResponse(userMsg),
                retrievedContext.citations(),
                promptMessages,
                assistantMsg != null ? assistantMsg.getId() : null);
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        chatStreamHandler.stopStream(sessionId);
        messageReportRepository.deleteBySessionId(sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.delete(session);
    }

    @Transactional
    public ChatSessionResponse renameSession(UUID userId, UUID sessionId, String newTitle) {
        ChatSession session = requireSession(userId, sessionId);
        if (newTitle == null || newTitle.isBlank()) {
            throw new BadRequestException("Title must not be blank");
        }
        String trimmed = newTitle.trim();
        if (trimmed.length() > 200) {
            throw new BadRequestException("Title cannot exceed 200 characters");
        }
        session.setTitle(trimmed);
        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    @Transactional
    public ChatSessionResponse branchSession(UUID userId, UUID sessionId, UUID cutoffMessageId, String newTitle) {
        ChatSession parentSession = requireSession(userId, sessionId);
        ChatMessage cutoffMessage = chatMessageRepository.findByIdAndSessionId(cutoffMessageId, sessionId)
                .orElseThrow(() -> new NotFoundException("Message not found in this session"));

        String title = (newTitle != null && !newTitle.isBlank())
                ? newTitle.trim()
                : "Branch: " + parentSession.getTitle();
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        ChatSession branchSession = ChatSession.builder()
                .userId(userId)
                .repositoryId(parentSession.getRepositoryId())
                .title(title)
                .parentSessionId(parentSession.getId())
                .branchMessageId(cutoffMessage.getId())
                .build();
        branchSession = chatSessionRepository.save(branchSession);

        List<ChatMessage> messagesToCopy = chatMessageRepository
                .findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(parentSession.getId(),
                        cutoffMessage.getCreatedAt());

        for (ChatMessage m : messagesToCopy) {
            chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(branchSession.getId())
                    .role(m.getRole())
                    .status(m.getStatus())
                    .content(m.getContent())
                    .citations(m.getCitations())
                    .createdAt(m.getCreatedAt())
                    .build());
        }

        return toSessionResponse(branchSession);
    }

    @Transactional
    public ShareResponse createShare(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        String token = session.getShareToken();
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            session.setShareToken(token);
        }
        session.setShared(true);
        session.setSharedAt(Instant.now());
        chatSessionRepository.save(session);
        return new ShareResponse(token, "/share/" + token);
    }

    @Transactional
    public void revokeShare(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        session.setShared(false);
        session.setShareToken(null);
        session.setSharedAt(null);
        chatSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public PublicSharedChatResponse getPublicShare(String shareToken) {
        if (shareToken == null || shareToken.isBlank()) {
            throw new NotFoundException("Shared chat not found");
        }
        ChatSession session = chatSessionRepository.findByShareTokenAndIsSharedTrue(shareToken)
                .orElseThrow(() -> new NotFoundException("Shared chat not found or has been revoked"));

        GitRepo repo = gitRepoService.getById(session.getRepositoryId());
        List<ChatMessageResponse> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(this::toMessageResponse)
                .toList();

        return new PublicSharedChatResponse(
                session.getTitle(),
                repo.getFullName(),
                session.getSharedAt(),
                messages);
    }

    @Transactional
    public void reportMessage(UUID userId, UUID sessionId, UUID messageId, ReportReason reason, String details) {
        requireSession(userId, sessionId);
        ChatMessage message = chatMessageRepository.findByIdAndSessionId(messageId, sessionId)
                .orElseThrow(() -> new NotFoundException("Message not found in this session"));

        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new BadRequestException("Only assistant messages can be reported");
        }

        if (messageReportRepository.existsByUserIdAndMessageId(userId, messageId)) {
            throw new BadRequestException("You have already reported this message");
        }

        MessageReport report = MessageReport.builder()
                .messageId(messageId)
                .sessionId(sessionId)
                .userId(userId)
                .reason(reason)
                .details(details != null && !details.isBlank() ? details.trim() : null)
                .createdAt(Instant.now())
                .build();

        messageReportRepository.save(report);
    }

    public void stopStream(UUID userId, UUID sessionId) {
        requireSession(userId, sessionId);
        chatStreamHandler.stopStream(sessionId);
    }

    public ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getRepositoryId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getParentSessionId(),
                session.getBranchMessageId(),
                session.isShared(),
                session.getShareToken());
    }

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        return chatStreamHandler.toMessageResponse(message);
    }
}