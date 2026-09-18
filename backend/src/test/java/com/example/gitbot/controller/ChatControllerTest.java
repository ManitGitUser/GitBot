package com.example.gitbot.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.gitbot.dto.BranchChatSessionRequest;
import com.example.gitbot.dto.ChatSessionResponse;
import com.example.gitbot.dto.RenameChatSessionRequest;
import com.example.gitbot.dto.ReportMessageRequest;
import com.example.gitbot.dto.ShareResponse;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.ReportReason;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import com.example.gitbot.security.AppUserPrincipal;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ChatService chatService;

    @InjectMocks
    private ChatController chatController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(chatController).build();
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        User user = User.builder().id(userId).githubUsername("testuser").build();
        AppUserPrincipal principal = new AppUserPrincipal(user, Map.of());
        when(currentUser.require()).thenReturn(principal);
    }

    @Test
    void deleteSession_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/chat/sessions/{id}", sessionId))
                .andExpect(status().isNoContent());

        verify(chatService).deleteSession(userId, sessionId);
    }

    @Test
    void renameSession_returnsUpdatedSession() throws Exception {
        RenameChatSessionRequest request = new RenameChatSessionRequest("New Name");
        ChatSessionResponse response = new ChatSessionResponse(
                sessionId, UUID.randomUUID(), "New Name", Instant.now(), null, null, false, null
        );

        when(chatService.renameSession(userId, sessionId, "New Name")).thenReturn(response);

        mockMvc.perform(patch("/api/chat/sessions/{id}", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Name"));
    }

    @Test
    void branchSession_returnsBranchedSession() throws Exception {
        UUID messageId = UUID.randomUUID();
        BranchChatSessionRequest request = new BranchChatSessionRequest(messageId, "Branch Name");
        ChatSessionResponse response = new ChatSessionResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Branch Name", Instant.now(), sessionId, messageId, false, null
        );

        when(chatService.branchSession(userId, sessionId, messageId, "Branch Name")).thenReturn(response);

        mockMvc.perform(post("/api/chat/sessions/{id}/branch", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Branch Name"))
                .andExpect(jsonPath("$.parentSessionId").value(sessionId.toString()));
    }

    @Test
    void shareSession_returnsShareResponse() throws Exception {
        ShareResponse response = new ShareResponse("token123", "/share/token123");
        when(chatService.createShare(userId, sessionId)).thenReturn(response);

        mockMvc.perform(post("/api/chat/sessions/{id}/share", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareToken").value("token123"))
                .andExpect(jsonPath("$.shareUrl").value("/share/token123"));
    }

    @Test
    void revokeShare_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/chat/sessions/{id}/share", sessionId))
                .andExpect(status().isNoContent());

        verify(chatService).revokeShare(userId, sessionId);
    }

    @Test
    void reportMessage_returnsCreated() throws Exception {
        UUID messageId = UUID.randomUUID();
        ReportMessageRequest request = new ReportMessageRequest(ReportReason.INCORRECT, "Not accurate");

        mockMvc.perform(post("/api/chat/sessions/{id}/messages/{messageId}/report", sessionId, messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(chatService).reportMessage(userId, sessionId, messageId, ReportReason.INCORRECT, "Not accurate");
    }

    @Test
    void stopStream_returnsOk() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/{id}/stop", sessionId))
                .andExpect(status().isOk());

        verify(chatService).stopStream(userId, sessionId);
    }
}
