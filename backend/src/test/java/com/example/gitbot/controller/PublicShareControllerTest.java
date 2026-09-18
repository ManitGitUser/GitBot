package com.example.gitbot.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.gitbot.dto.ChatMessageResponse;
import com.example.gitbot.dto.PublicSharedChatResponse;
import com.example.gitbot.enums.MessageRole;
import com.example.gitbot.enums.MessageStatus;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicShareControllerTest {

    @Mock
    private ChatService chatService;

    @InjectMocks
    private PublicShareController publicShareController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publicShareController).build();
    }

    @Test
    void getPublicShare_returnsSharedChat() throws Exception {
        String token = "share123";
        ChatMessageResponse msg = new ChatMessageResponse(
                UUID.randomUUID(),
                MessageRole.ASSISTANT,
                MessageStatus.COMPLETE,
                "Hello shared world",
                List.of(),
                Instant.now()
        );
        PublicSharedChatResponse response = new PublicSharedChatResponse(
                "My Shared Conversation",
                "octocat/Hello-World",
                Instant.now(),
                List.of(msg)
        );

        when(chatService.getPublicShare(token)).thenReturn(response);

        mockMvc.perform(get("/api/public/shares/{shareToken}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My Shared Conversation"))
                .andExpect(jsonPath("$.repoFullName").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.messages[0].content").value("Hello shared world"));
    }

    @Test
    void getPublicShare_returnsNotFound_whenTokenMissing() throws Exception {
        when(chatService.getPublicShare("missing")).thenThrow(new NotFoundException("Shared chat not found"));

        mockMvc.perform(get("/api/public/shares/{shareToken}", "missing"))
                .andExpect(status().isNotFound());
    }
}
