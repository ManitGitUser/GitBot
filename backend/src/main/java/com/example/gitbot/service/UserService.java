package com.example.gitbot.service;

import com.example.gitbot.entity.User;
import com.example.gitbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepo;
    private final TextEncryptor tokenEncryptor;

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

    public User upsertFromGitHub(Map<String, Object> attributes, String accessToken, String scopes) {

        Long githubId = toLong(attributes.get("id"));
        String login = String.valueOf(attributes.get("login"));
        String name = attributes.get("name").toString() != null
                        ?
                        attributes.get("name").toString()
                        : login ;
        String avatarUrl = attributes.get("avatar_url").toString() != null
                        ?
                        attributes.get("avatar_url").toString()
                        : null;

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
}
