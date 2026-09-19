package com.example.gitbot.service.ai;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.example.gitbot.dto.ChatMessageResponse;
import com.example.gitbot.dto.CitationDto;
import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;
import com.example.gitbot.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Generation step: call OpenAI via Spring AI and stream tokens to the browser over SSE.
 *
 * <p>Supports active stream tracking, thread-safe cancellation via Reactor Disposable,
 * client disconnect detection, retry replacement, and message status updates (COMPLETE, INTERRUPTED, FAILED).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStreamHandler {

    private final ChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final CitationMapper citationMapper;

    private final ConcurrentHashMap<UUID, ActiveChatStream> activeStreams = new ConcurrentHashMap<>();

    private static class ActiveChatStream {
        final UUID sessionId;
        final SseEmitter emitter;
        final StringBuilder fullReply = new StringBuilder();
        final List<CitationDto> citations;
        final UUID targetAssistantMessageId;
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        volatile Disposable subscription;

        ActiveChatStream(UUID sessionId, SseEmitter emitter, List<CitationDto> citations, UUID targetAssistantMessageId) {
            this.sessionId = sessionId;
            this.emitter = emitter;
            this.citations = citations;
            this.targetAssistantMessageId = targetAssistantMessageId;
        }
    }

    public boolean isStreamActive(UUID sessionId) {
        ActiveChatStream active = activeStreams.get(sessionId);
        return active != null && !active.completed.get() && !active.stopped.get();
    }

    public SseEmitter stream(
            UUID sessionId,
            ChatMessageResponse userMessage,
            List<CitationDto> citations,
            List<Message> messages,
            UUID targetAssistantMessageId
    ) {
        // Cancel any existing active stream for this session
        stopStream(sessionId);

        SseEmitter emitter = new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);
        ActiveChatStream activeStream = new ActiveChatStream(sessionId, emitter, citations, targetAssistantMessageId);
        activeStreams.put(sessionId, activeStream);

        emitter.onCompletion(() -> activeStreams.remove(sessionId, activeStream));

        emitter.onTimeout(() -> {
            log.warn("SSE emitter timed out for session {}", sessionId);
            handleInterruption(activeStream);
            activeStreams.remove(sessionId, activeStream);
        });

        emitter.onError(err -> {
            log.warn("SSE emitter error for session {}: {}", sessionId, err.getMessage());
            handleInterruption(activeStream);
            activeStreams.remove(sessionId, activeStream);
        });

        try {
            if (userMessage != null) {
                emitter.send(
                        SseEmitter.event()
                                .name("user_message")
                                .data(userMessage)
                );
            }

            Disposable subscription = ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .messages(messages)
                    .stream()
                    .content()
                    .doOnNext(token -> appendToken(activeStream, token))
                    .doOnError(err -> {
                        log.error("Chat stream error for session {}", sessionId, err);
                        handleFailure(activeStream, err);
                        activeStreams.remove(sessionId, activeStream);
                    })
                    .doOnComplete(() -> {
                        completeStream(activeStream);
                        activeStreams.remove(sessionId, activeStream);
                    })
                    .subscribe();

            activeStream.subscription = subscription;

        } catch (Exception ex) {
            log.error("Failed to initiate chat stream", ex);
            handleFailure(activeStream, ex);
            activeStreams.remove(sessionId, activeStream);
        }

        return emitter;
    }

    public boolean stopStream(UUID sessionId) {
        ActiveChatStream active = activeStreams.remove(sessionId);
        if (active != null && !active.completed.get()) {
            active.stopped.set(true);
            if (active.subscription != null && !active.subscription.isDisposed()) {
                active.subscription.dispose();
            }
            handleInterruption(active);
            return true;
        }
        return false;
    }

    private void appendToken(ActiveChatStream stream, String token) {
        if (stream.stopped.get() || stream.completed.get()) {
            return;
        }
        stream.fullReply.append(token);
        try {
            stream.emitter.send(
                    SseEmitter.event()
                            .name("token")
                            .data(token, MediaType.APPLICATION_JSON)
            );
        } catch (Exception ex) {
            log.debug("Client disconnected while sending token for session {}", stream.sessionId);
            stream.stopped.set(true);
            if (stream.subscription != null && !stream.subscription.isDisposed()) {
                stream.subscription.dispose();
            }
            handleInterruption(stream);
            activeStreams.remove(stream.sessionId, stream);
        }
    }

    private void completeStream(ActiveChatStream stream) {
        if (!stream.completed.compareAndSet(false, true)) {
            return;
        }
        try {
            String fullReply = stream.fullReply.toString();
            List<CitationDto> supporting = citationMapper.filterSupportingCitations(fullReply, stream.citations);

            ChatMessage assistant = persistAssistantMessage(
                    stream.sessionId,
                    stream.targetAssistantMessageId,
                    fullReply,
                    supporting,
                    MessageStatus.COMPLETE
            );

            stream.emitter.send(
                    SseEmitter.event()
                            .name("assistant_message")
                            .data(toMessageResponse(assistant))
            );
            stream.emitter.send(
                    SseEmitter.event()
                            .name("done")
                            .data("[DONE]")
            );
            stream.emitter.complete();

        } catch (Exception ex) {
            log.error("Failed to complete chat stream", ex);
            try {
                stream.emitter.completeWithError(ex);
            } catch (Exception ignored) {}
        }
    }

    private void handleInterruption(ActiveChatStream stream) {
        if (!stream.completed.compareAndSet(false, true)) {
            return;
        }
        try {
            String partial = stream.fullReply.toString();
            List<CitationDto> supporting = citationMapper.filterSupportingCitations(partial, stream.citations);
            ChatMessage assistant = persistAssistantMessage(
                    stream.sessionId,
                    stream.targetAssistantMessageId,
                    partial.isBlank() ? "Generation interrupted." : partial,
                    supporting,
                    MessageStatus.INTERRUPTED
            );

            try {
                stream.emitter.send(
                        SseEmitter.event()
                                .name("assistant_message")
                                .data(toMessageResponse(assistant))
                );
                stream.emitter.send(
                        SseEmitter.event()
                                .name("done")
                                .data("[DONE]")
                );
                stream.emitter.complete();
            } catch (Exception ignored) {}
        } catch (Exception ex) {
            log.error("Error saving interrupted message", ex);
        }
    }

    private void handleFailure(ActiveChatStream stream, Throwable err) {
        if (!stream.completed.compareAndSet(false, true)) {
            return;
        }
        try {
            String partial = stream.fullReply.toString();
            ChatMessage assistant = persistAssistantMessage(
                    stream.sessionId,
                    stream.targetAssistantMessageId,
                    partial.isBlank() ? "Generation failed: " + err.getMessage() : partial,
                    List.of(),
                    MessageStatus.FAILED
            );

            try {
                stream.emitter.send(
                        SseEmitter.event()
                                .name("assistant_message")
                                .data(toMessageResponse(assistant))
                );
                stream.emitter.send(
                        SseEmitter.event()
                                .name("error")
                                .data(err.getMessage())
                );
                stream.emitter.completeWithError(err);
            } catch (Exception ignored) {}
        } catch (Exception ex) {
            log.error("Error saving failed message", ex);
        }
    }

    private ChatMessage persistAssistantMessage(
            UUID sessionId,
            UUID targetAssistantMessageId,
            String content,
            List<CitationDto> citations,
            MessageStatus status
    ) {
        if (targetAssistantMessageId != null) {
            var existing = chatMessageRepository.findById(targetAssistantMessageId);
            if (existing.isPresent()) {
                ChatMessage m = existing.get();
                m.setContent(content);
                m.setStatus(status);
                m.setCitations(citationMapper.toJson(citations));
                return chatMessageRepository.save(m);
            }
        }
        return chatMessageRepository.save(
                ChatMessage.builder()
                        .sessionId(sessionId)
                        .role(MessageRole.ASSISTANT)
                        .status(status)
                        .content(content)
                        .citations(citationMapper.toJson(citations))
                        .build()
        );
    }

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        List<CitationDto> citations = citationMapper.fromJson(message.getCitations());
        if (message.getRole() == MessageRole.ASSISTANT && message.getContent() != null) {
            citations = citationMapper.filterSupportingCitations(message.getContent(), citations);
        }
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getStatus() != null ? message.getStatus() : MessageStatus.COMPLETE,
                message.getContent(),
                citations,
                message.getCreatedAt()
        );
    }
}