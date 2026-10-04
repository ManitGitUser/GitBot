package com.example.gitbot.service.demo;

import com.example.gitbot.dto.ChatMessageResponse;
import com.example.gitbot.dto.CitationDto;
import com.example.gitbot.dto.DemoChatMessageDto;
import com.example.gitbot.dto.DemoChatRequest;
import com.example.gitbot.dto.DemoStatusResponse;
import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;
import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.ConflictException;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.service.ai.ChatPromptBuilder;
import com.example.gitbot.service.ai.CitationMapper;
import com.example.gitbot.service.ai.CodeContextRetriever;
import com.example.gitbot.service.ai.RagSettings;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demo chat service providing unauthenticated, zero-persistence RAG chat streaming
 * strictly isolated to GitBot's own codebase.
 */
@Service
@Slf4j
public class DemoChatService {

    private final GitRepoRepository gitRepoRepository;
    private final CodeContextRetriever codeContextRetriever;
    private final ChatPromptBuilder chatPromptBuilder;
    private final CitationMapper citationMapper;
    private final ChatModel chatModel;
    private final DemoSessionRedisService demoSessionRedisService;
    private final DemoRateLimiter demoRateLimiter;
    private final String configuredRepoFullName;
    private final int maxMessages;
    private final int maxMessageChars;

    public DemoChatService(
            GitRepoRepository gitRepoRepository,
            CodeContextRetriever codeContextRetriever,
            ChatPromptBuilder chatPromptBuilder,
            CitationMapper citationMapper,
            ChatModel chatModel,
            DemoSessionRedisService demoSessionRedisService,
            DemoRateLimiter demoRateLimiter,
            @Value("${app.demo.repo-full-name:ManitGitUser/GitBot}") String configuredRepoFullName,
            @Value("${app.demo.max-messages:5}") int maxMessages,
            @Value("${app.demo.max-message-chars:1000}") int maxMessageChars
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.codeContextRetriever = codeContextRetriever;
        this.chatPromptBuilder = chatPromptBuilder;
        this.citationMapper = citationMapper;
        this.chatModel = chatModel;
        this.demoSessionRedisService = demoSessionRedisService;
        this.demoRateLimiter = demoRateLimiter;
        this.configuredRepoFullName = configuredRepoFullName;
        this.maxMessages = maxMessages;
        this.maxMessageChars = maxMessageChars;
    }

    /**
     * Resolves the fixed demo repository record without accepting any client repo identifier.
     */
    public GitRepo resolveDemoRepo() {
        return gitRepoRepository.findFirstByIsDemoTrue()
                .or(() -> gitRepoRepository.findByFullName(configuredRepoFullName))
                .orElse(null);
    }

    /**
     * Returns the public status of the demo service, including IP-specific remaining messages if clientIp is known.
     */
    public DemoStatusResponse getDemoStatus(String clientIp) {
        GitRepo repo = resolveDemoRepo();
        boolean ready = repo != null && repo.getIndexStatus() == IndexStatus.READY;
        String name = repo != null ? repo.getName() : "GitBot";
        String fullName = repo != null ? repo.getFullName() : configuredRepoFullName;
        int remaining = demoSessionRedisService.getIpRemainingMessages(clientIp);
        return new DemoStatusResponse(ready, name, fullName, maxMessages, remaining);
    }

    public DemoStatusResponse getDemoStatus() {
        return getDemoStatus(null);
    }

    /**
     * Streams demo chat response over SSE without persisting anything to the database.
     */
    public SseEmitter streamDemoChat(DemoChatRequest request, String clientIp, HttpServletResponse httpResponse) {
        // 1. Abuse prevention: rate limiting
        demoRateLimiter.checkRateLimit(clientIp);

        // 2. Input validation
        if (request.message() == null || request.message().trim().isEmpty()) {
            throw new BadRequestException("Message cannot be empty.");
        }
        String userQuestion = request.message().trim();
        if (userQuestion.length() > maxMessageChars) {
            throw new BadRequestException("Message exceeds maximum length of " + maxMessageChars + " characters.");
        }

        // 3. Resolve fixed demo repository (strictly server-authoritative) BEFORE consuming message quota
        GitRepo repo = resolveDemoRepo();
        if (repo == null) {
            throw new ConflictException("Demo repository is not registered in the system.");
        }
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new ConflictException("Demo repository is currently not indexed.");
        }

        // 4. Demo session verification & atomic increment via Redis (authoritative per IP)
        DemoSessionRedisService.DemoSessionResult sessionResult = demoSessionRedisService.getOrIncrementSession(request.demoToken(), clientIp);
        if (httpResponse != null) {
            httpResponse.setHeader("X-Demo-Token", sessionResult.token());
        }

        // 5. RAG retrieval strictly isolated to the demo repository
        var retrievedContext = codeContextRetriever.retrieve(repo.getId(), userQuestion);

        // 6. Build in-memory conversation history (NO database persistence)
        List<ChatMessage> historyMessages = new ArrayList<>();
        if (request.history() != null && !request.history().isEmpty()) {
            int startIdx = Math.max(0, request.history().size() - ChatPromptBuilder.MAX_HISTORY_MESSAGES);
            for (int i = startIdx; i < request.history().size(); i++) {
                DemoChatMessageDto item = request.history().get(i);
                if (item != null && item.content() != null && !item.content().isBlank()) {
                    MessageRole role = "USER".equalsIgnoreCase(item.role()) ? MessageRole.USER : MessageRole.ASSISTANT;
                    historyMessages.add(ChatMessage.builder()
                            .role(role)
                            .content(item.content().trim())
                            .build());
                }
            }
        }

        // 7. Prompt assembly
        List<Message> promptMessages = chatPromptBuilder.buildMessages(
                repo.getFullName(),
                historyMessages,
                retrievedContext.contextText(),
                userQuestion
        );

        // 8. Stream execution (ZERO DB saves)
        SseEmitter emitter = new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);
        StringBuilder fullReply = new StringBuilder();
        AtomicBoolean completed = new AtomicBoolean(false);
        final Disposable[] subscriptionHolder = new Disposable[1];

        emitter.onCompletion(() -> {
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Demo SSE emitter timed out");
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
            emitter.complete();
        });

        emitter.onError(err -> {
            log.warn("Demo SSE emitter error: {}", err.getMessage());
            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                subscriptionHolder[0].dispose();
            }
        });

        try {
            // Send updated demo token
            emitter.send(
                    SseEmitter.event()
                            .name("demo_token")
                            .data(sessionResult.token())
            );

            // Send transient user message response
            ChatMessageResponse transientUserMessage = new ChatMessageResponse(
                    UUID.randomUUID(),
                    MessageRole.USER,
                    MessageStatus.COMPLETE,
                    userQuestion,
                    Collections.emptyList(),
                    Instant.now()
            );
            emitter.send(
                    SseEmitter.event()
                            .name("user_message")
                            .data(transientUserMessage)
            );

            // Subscribe to OpenAI chat stream
            Disposable subscription = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .messages(promptMessages)
                    .stream()
                    .content()
                    .doOnNext(token -> {
                        if (completed.get()) return;
                        fullReply.append(token);
                        try {
                            emitter.send(
                                    SseEmitter.event()
                                            .name("token")
                                            .data(token, MediaType.APPLICATION_JSON)
                            );
                        } catch (Exception ex) {
                            log.debug("Demo client disconnected while sending token");
                            if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                                subscriptionHolder[0].dispose();
                            }
                            emitter.complete();
                        }
                    })
                    .doOnError(err -> {
                        log.error("Error in demo chat stream", err);
                        try {
                            emitter.send(
                                    SseEmitter.event()
                                            .name("error")
                                            .data("An error occurred during generation: " + err.getMessage())
                            );
                            emitter.complete();
                        } catch (Exception ignored) {
                        }
                    })
                    .doOnComplete(() -> {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        try {
                            String reply = fullReply.toString();
                            List<CitationDto> supporting = citationMapper.filterSupportingCitations(reply, retrievedContext.citations());

                            // Ephemeral assistant message response (NOT persisted to DB)
                            ChatMessageResponse transientAssistantMessage = new ChatMessageResponse(
                                    UUID.randomUUID(),
                                    MessageRole.ASSISTANT,
                                    MessageStatus.COMPLETE,
                                    reply,
                                    supporting,
                                    Instant.now()
                            );

                            emitter.send(
                                    SseEmitter.event()
                                            .name("assistant_message")
                                            .data(transientAssistantMessage)
                            );
                            emitter.send(
                                    SseEmitter.event()
                                            .name("done")
                                            .data("[DONE]")
                            );
                            emitter.complete();
                        } catch (Exception ex) {
                            log.error("Failed to complete demo stream", ex);
                            try {
                                emitter.completeWithError(ex);
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    .subscribe();

            subscriptionHolder[0] = subscription;

        } catch (Exception ex) {
            log.error("Failed to initialize demo stream", ex);
            try {
                emitter.completeWithError(ex);
            } catch (Exception ignored) {
            }
        }

        return emitter;
    }
}
