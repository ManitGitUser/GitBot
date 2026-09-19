package com.example.gitbot.service.ai;

import java.util.ArrayList;
import java.util.List;

import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.enums.MessageRole;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Builds the prompt messages sent to OpenAI.
 *
 * <p>
 * Supports multi-turn conversation history bounded to
 * {@value #MAX_HISTORY_MESSAGES} turns.
 * The structure sent to the LLM:
 * <ol>
 * <li>System message with persona and grounding constraints</li>
 * <li>Prior conversation turns (alternating User and Assistant messages)</li>
 * <li>Current turn User message containing retrieved code context and the
 * current question</li>
 * </ol>
 */
@Component
public class ChatPromptBuilder {

    public static final int MAX_HISTORY_MESSAGES = 10;

    public String systemPrompt(String repositoryFullName) {
        return """
                You are GitBot, an expert technical assistant for the %s codebase.
                Use the provided repository code context to answer the user's questions accurately.
                The code context contains authentic source chunks from the repository, delimited by <source> blocks with exact file paths and line ranges.
                Cite relevant file paths and line ranges when referencing code. Always mention the source file path when discussing code from the repository.
                If the question is casual conversation, respond conversationally without citing files.
                If the provided context is insufficient or irrelevant to answer the question, clearly state that rather than fabricating code or assumptions, and do not cite any files.
                Do not invent files, APIs, functions, or line numbers not grounded in the context.
                Be concise, precise, and technical.
                """
                .formatted(repositoryFullName);
    }

    public String userPrompt(String codeContext, String question) {
        return """
                Repository Code Context:
                %s

                User Question:
                %s
                """.formatted(codeContext, question);
    }

    public List<Message> buildMessages(
            String repositoryFullName,
            List<ChatMessage> historyMessages,
            String codeContext,
            String currentQuestion) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt(repositoryFullName)));

        if (historyMessages != null && !historyMessages.isEmpty()) {
            int startIdx = Math.max(0, historyMessages.size() - MAX_HISTORY_MESSAGES);
            for (int i = startIdx; i < historyMessages.size(); i++) {
                ChatMessage m = historyMessages.get(i);
                if (m.getContent() == null || m.getContent().isBlank()) {
                    continue;
                }
                if (m.getRole() == MessageRole.USER) {
                    messages.add(new UserMessage(m.getContent()));
                } else if (m.getRole() == MessageRole.ASSISTANT) {
                    messages.add(new AssistantMessage(m.getContent()));
                }
            }
        }

        messages.add(new UserMessage(userPrompt(codeContext, currentQuestion)));
        return messages;
    }
}
