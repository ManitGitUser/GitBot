package com.example.gitbot.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.example.gitbot.dto.IndexStatusResponse;
import com.example.gitbot.entity.User;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.GlobalExceptionHandler;
import com.example.gitbot.exception.NotFoundException;
import com.example.gitbot.security.AppUserPrincipal;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.GitRepoService;
import com.example.gitbot.service.indexing.IndexingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitRepoControllerTest {

    @Mock
    private GitRepoService gitRepoService;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private IndexingService indexingService;

    @InjectMocks
    private GitRepoController gitRepoController;

    private MockMvc mockMvc;
    private UUID userId;
    private UUID repoId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(gitRepoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        userId = UUID.randomUUID();
        repoId = UUID.randomUUID();

        User user = User.builder().id(userId).githubUsername("testuser").build();
        AppUserPrincipal principal = new AppUserPrincipal(user, Map.of());
        when(currentUser.require()).thenReturn(principal);
    }

    @Test
    void status_returnsExpectedResponse_withCorrectArgumentOrdering() throws Exception {
        IndexStatusResponse expectedResponse = new IndexStatusResponse(
                repoId,
                IndexStatus.READY,
                15,
                15,
                120,
                Instant.now(),
                null
        );

        when(gitRepoService.status(repoId, userId)).thenReturn(expectedResponse);

        mockMvc.perform(get("/api/repos/{id}/status", repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId.toString()))
                .andExpect(jsonPath("$.indexStatus").value("READY"))
                .andExpect(jsonPath("$.filesTotal").value(15))
                .andExpect(jsonPath("$.filesProcessed").value(15))
                .andExpect(jsonPath("$.chunkCount").value(120));

        // Explicitly verify argument ordering: status(UUID repoId, UUID userId)
        // If arguments were reversed, this verification would fail
        verify(gitRepoService).status(repoId, userId);
    }

    @Test
    void status_whenRepoNotOwnedOrNotFound_returnsNotFound() throws Exception {
        when(gitRepoService.status(repoId, userId))
                .thenThrow(new NotFoundException("Repository not found"));

        mockMvc.perform(get("/api/repos/{id}/status", repoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Repository not found"));

        verify(gitRepoService).status(repoId, userId);
    }
}
