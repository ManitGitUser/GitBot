package com.example.gitbot.controller;

import com.example.gitbot.dto.DemoChatRequest;
import com.example.gitbot.dto.DemoStatusResponse;
import com.example.gitbot.exception.GlobalExceptionHandler;
import com.example.gitbot.exception.TooManyRequestsException;
import com.example.gitbot.service.demo.DemoChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

    @Mock
    private DemoChatService demoChatService;

    @InjectMocks
    private DemoController demoController;

    private MockMvc mockMvc;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(demoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDemoStatus_returnsOk() throws Exception {
        when(demoChatService.getDemoStatus()).thenReturn(
                new DemoStatusResponse(true, "GitBot", "ManitGitUser/GitBot", 5)
        );

        mockMvc.perform(get("/api/demo/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.repoName").value("GitBot"))
                .andExpect(jsonPath("$.repoFullName").value("ManitGitUser/GitBot"))
                .andExpect(jsonPath("$.maxMessages").value(5));
    }

    @Test
    void postDemoChat_validRequest_returnsOk() throws Exception {
        DemoChatRequest request = new DemoChatRequest("How does authentication work?", List.of(), null);
        SseEmitter emitter = new SseEmitter();
        when(demoChatService.streamDemoChat(any(), any(), any())).thenReturn(emitter);

        mockMvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void postDemoChat_emptyMessage_returnsBadRequest() throws Exception {
        DemoChatRequest request = new DemoChatRequest("   ", List.of(), null);

        mockMvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postDemoChat_oversizedMessage_returnsBadRequest() throws Exception {
        String longMessage = "a".repeat(1001);
        DemoChatRequest request = new DemoChatRequest(longMessage, List.of(), null);

        mockMvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postDemoChat_exceededLimit_returnsTooManyRequests() throws Exception {
        DemoChatRequest request = new DemoChatRequest("Sixth message", List.of(), "token-at-max");
        when(demoChatService.streamDemoChat(any(), any(), any()))
                .thenThrow(new TooManyRequestsException("Demo limit reached. Sign in with GitHub to continue using GitBot."));

        mockMvc.perform(post("/api/demo/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Demo limit reached. Sign in with GitHub to continue using GitBot."));
    }
}
