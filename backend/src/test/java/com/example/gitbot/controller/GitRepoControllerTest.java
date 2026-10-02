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
    void removeIndex_success_returnsOkAndRepoResponse() throws Exception {
        com.example.gitbot.entity.GitRepo repo = com.example.gitbot.entity.GitRepo.builder()
                .id(repoId)
                .userId(userId)
                .fullName("testuser/test-repo")
                .indexStatus(IndexStatus.PENDING)
                .chunkCount(0)
                .build();
        com.example.gitbot.dto.GitRepoResponse expectedResponse = new com.example.gitbot.dto.GitRepoResponse(
                repoId, 12345L, "testuser", "test-repo", "testuser/test-repo", false,
                "main", "Java", "https://github.com/testuser/test-repo", "Description",
                IndexStatus.PENDING, null, 0, 0, 0, null, null, null
        );

        when(indexingService.removeIndex(repoId, userId)).thenReturn(repo);
        when(gitRepoService.toResponse(repo)).thenReturn(expectedResponse);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/repos/{id}/index", repoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(repoId.toString()))
                .andExpect(jsonPath("$.indexStatus").value("PENDING"))
                .andExpect(jsonPath("$.chunkCount").value(0));

        verify(indexingService).removeIndex(repoId, userId);
        verify(gitRepoService).toResponse(repo);
    }

    @Test
    void removeIndex_whenIndexingInProgress_returnsConflict() throws Exception {
        when(indexingService.removeIndex(repoId, userId))
                .thenThrow(new com.example.gitbot.exception.ConflictException("Cannot remove index while indexing is in progress"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/repos/{id}/index", repoId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot remove index while indexing is in progress"));

        verify(indexingService).removeIndex(repoId, userId);
    }

    @Test
    void removeIndex_whenRepoNotFound_returnsNotFound() throws Exception {
        when(indexingService.removeIndex(repoId, userId))
                .thenThrow(new NotFoundException("Repository not found"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/repos/{id}/index", repoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Repository not found"));

        verify(indexingService).removeIndex(repoId, userId);
    }
}

