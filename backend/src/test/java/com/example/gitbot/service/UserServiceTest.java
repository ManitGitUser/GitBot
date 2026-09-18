package com.example.gitbot.service;

import com.example.gitbot.entity.User;
import com.example.gitbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TextEncryptor tokenEncryptor;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(tokenEncryptor.encrypt(any())).thenAnswer(inv -> "enc_" + inv.getArgument(0));
    }

    @Test
    void upsertFromGitHub_handlesNullAvatarUrlWithoutNpe() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 12345L);
        attributes.put("login", "octocat");
        attributes.put("name", "The Octocat");
        attributes.put("avatar_url", null);

        when(userRepository.findByGithubId(12345L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.upsertFromGitHub(attributes, "gho_secret123", "read:user,repo");

        assertThat(user).isNotNull();
        assertThat(user.getGithubId()).isEqualTo(12345L);
        assertThat(user.getGithubUsername()).isEqualTo("octocat");
        assertThat(user.getDisplayName()).isEqualTo("The Octocat");
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.getAccessToken()).isEqualTo("enc_gho_secret123");
        assertThat(user.getTokenScopes()).isEqualTo("read:user,repo");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void upsertFromGitHub_setsAvatarUrlWhenPresent() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", 67890);
        attributes.put("login", "monalisa");
        attributes.put("avatar_url", "https://github.com/images/error/octocat_happy.gif");

        when(userRepository.findByGithubId(67890L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.upsertFromGitHub(attributes, "gho_token", "read:user");

        assertThat(user.getDisplayName()).isEqualTo("monalisa");
        assertThat(user.getAvatarUrl()).isEqualTo("https://github.com/images/error/octocat_happy.gif");
    }
}
