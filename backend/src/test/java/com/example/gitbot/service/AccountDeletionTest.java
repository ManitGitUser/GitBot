package com.example.gitbot.service;

import com.example.gitbot.entity.ChatMessage;
import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.repository.MessageReportRepository;
import com.example.gitbot.repository.UserRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TextEncryptor tokenEncryptor;

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private GitRepoRepository gitRepoRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private MessageReportRepository messageReportRepository;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private User user;
    private UUID repoId1;
    private UUID repoId2;
    private UUID sessionId1;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .githubId(123456L)
                .githubUsername("testuser")
                .displayName("Test User")
                .avatarUrl("https://avatars.githubusercontent.com/u/123456")
                .build();

        repoId1 = UUID.randomUUID();
        repoId2 = UUID.randomUUID();
        sessionId1 = UUID.randomUUID();
    }

    @Test
    @DisplayName("deleteAccount successfully deletes all user-owned data in order including vector_store")
    void deleteAccount_successfullyDeletesAllUserOwnedData() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ChatSession session = ChatSession.builder()
                .id(sessionId1)
                .userId(userId)
                .repositoryId(repoId1)
                .title("Test Chat")
                .build();
        when(chatSessionRepository.findByUserId(userId)).thenReturn(List.of(session));

        GitRepo repo1 = GitRepo.builder().id(repoId1).userId(userId).name("repo1").fullName("testuser/repo1").build();
        GitRepo repo2 = GitRepo.builder().id(repoId2).userId(userId).name("repo2").fullName("testuser/repo2").build();
        when(gitRepoRepository.findByUserId(userId)).thenReturn(List.of(repo1, repo2));

        userService.deleteAccount(userId);

        // 1. Chat sessions & messages & reports deletion
        verify(messageReportRepository).deleteBySessionId(sessionId1);
        verify(chatMessageRepository).deleteBySessionId(sessionId1);
        verify(messageReportRepository).deleteByUserId(userId);
        verify(chatSessionRepository).deleteAll(List.of(session));

        // 2. Vector store deletion for every repo
        verify(vectorStore, times(2)).delete(any(Filter.Expression.class));
        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'repoId' = ?", repoId1.toString());
        verify(jdbcTemplate).update("DELETE FROM vector_store WHERE metadata->>'repoId' = ?", repoId2.toString());

        // 3. Git repositories deletion
        verify(gitRepoRepository).deleteAll(List.of(repo1, repo2));

        // 4. User entity deletion
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("deleteAccount throws exception when user does not exist")
    void deleteAccount_throwsWhenUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteAccount(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verifyNoInteractions(gitRepoRepository);
        verifyNoInteractions(chatSessionRepository);
        verifyNoInteractions(vectorStore);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteAccount propagates exceptions for transaction rollback")
    void deleteAccount_propagatesExceptionsForRollback() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(chatSessionRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> userService.deleteAccount(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        verify(userRepository, never()).delete(any());
        verifyNoInteractions(gitRepoRepository);
    }
}
