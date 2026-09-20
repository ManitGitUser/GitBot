package com.example.gitbot.service;

import com.example.gitbot.entity.ChatSession;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.entity.User;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.repository.MessageReportRepository;
import com.example.gitbot.repository.UserRepository;
import com.example.gitbot.service.github.GitHubApiClient;
import com.example.gitbot.service.ai.RagSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final TextEncryptor tokenEncryptor;
    private final GitHubApiClient gitHubApiClient;
    private final GitRepoRepository gitRepoRepo;
    private final ChatSessionRepository chatSessionRepo;
    private final ChatMessageRepository chatMessageRepo;
    private final MessageReportRepository messageReportRepo;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public Optional<User> getByGithubId(Long id) {
        return userRepo.findByGithubId(id);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public String decryptAccessToken(User user) {
        return tokenEncryptor.decrypt(user.getAccessToken());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    static String normalizeAvatarUrl(Object avatarObj) {
        if (avatarObj == null) {
            return null;
        }
        String url = avatarObj.toString().trim();
        if (url.isEmpty()) {
            return null;
        }
        // Normalize [label](url) markdown link syntax to raw url if present
        if (url.startsWith("[") && url.contains("](") && url.endsWith(")")) {
            int start = url.indexOf("](") + 2;
            int end = url.length() - 1;
            url = url.substring(start, end).trim();
        }
        return url;
    }

    public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {

        Long githubId = toLong(attributes.get("id"));
        String login = String.valueOf(attributes.get("login"));
        String name = attributes.get("name") != null
                ? attributes.get("name").toString()
                : login;
        Object avatarObj = attributes.get("avatar_url");
        String avatarUrl = normalizeAvatarUrl(avatarObj);

        String encryptedToken = tokenEncryptor.encrypt(accessToken);

        User user = userRepo.findByGithubId(githubId).orElseGet(User::new);
        user.setGithubId(githubId);
        user.setGithubUsername(login);
        user.setDisplayName(name);
        user.setAvatarUrl(avatarUrl);
        user.setAccessToken(encryptedToken);
        user.setTokenScopes(scopes);

        return userRepo.save(user);
    }

    @Transactional
    public User syncProfile(UUID userId) {
        User user = getById(userId);
        String token = decryptAccessToken(user);
        Map<String, Object> profile = gitHubApiClient.getCurrentUserProfile(token);
        if (profile != null) {
            if (profile.get("login") != null) {
                user.setGithubUsername(String.valueOf(profile.get("login")));
            }
            if (profile.get("name") != null && !String.valueOf(profile.get("name")).isBlank()) {
                user.setDisplayName(String.valueOf(profile.get("name")));
            } else if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
                user.setDisplayName(user.getGithubUsername());
            }
            if (profile.get("avatar_url") != null) {
                user.setAvatarUrl(normalizeAvatarUrl(profile.get("avatar_url")));
            }
            user = userRepo.save(user);
        }
        return user;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = getById(userId);

        // 1. Delete message reports for all user sessions and reports submitted by this user
        List<ChatSession> sessions = chatSessionRepo.findByUserId(userId);
        for (ChatSession session : sessions) {
            messageReportRepo.deleteBySessionId(session.getId());
            chatMessageRepo.deleteBySessionId(session.getId());
        }
        messageReportRepo.deleteByUserId(userId);
        chatSessionRepo.deleteAll(sessions);

        // 2. Delete repositories and their vector store records
        List<GitRepo> repos = gitRepoRepo.findByUserId(userId);
        for (GitRepo repo : repos) {
            String repoIdStr = repo.getId().toString();
            if (vectorStore != null) {
                try {
                    var filter = new FilterExpressionBuilder().eq(RagSettings.METADATA_REPO_ID, repoIdStr).build();
                    vectorStore.delete(filter);
                } catch (Exception ex) {
                    // Ignored or logged if vector store encounters an issue
                }
            }
            if (jdbcTemplate != null) {
                jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'repoId' = ?", repoIdStr);
            }
        }
        gitRepoRepo.deleteAll(repos);

        // 3. Delete user entity
        userRepo.delete(user);
    }
}
