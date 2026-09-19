package com.example.gitbot.controller;

import com.example.gitbot.entity.User;
import com.example.gitbot.security.AppUserPrincipal;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private CurrentUser currentUser;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(currentUser, userService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("GET /api/auth/me returns avatarUrl when user has an avatar")
    void me_returnsAvatarUrl_whenPresent() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .githubId(197362476L)
                .githubUsername("ManitGitUser")
                .displayName("Manit")
                .avatarUrl("https://avatars.githubusercontent.com/u/197362476?v=4")
                .build();

        AppUserPrincipal principal = new AppUserPrincipal(user, Map.of("id", 197362476L));
        when(currentUser.require()).thenReturn(principal);
        when(userService.getById(userId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.githubId").value(197362476L))
                .andExpect(jsonPath("$.githubUsername").value("ManitGitUser"))
                .andExpect(jsonPath("$.displayName").value("Manit"))
                .andExpect(jsonPath("$.avatarUrl").value("https://avatars.githubusercontent.com/u/197362476?v=4"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns null avatarUrl when user has no avatar")
    void me_returnsNullAvatarUrl_whenMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .githubId(12345L)
                .githubUsername("testuser")
                .displayName("Test User")
                .avatarUrl(null)
                .build();

        AppUserPrincipal principal = new AppUserPrincipal(user, Map.of("id", 12345L));
        when(currentUser.require()).thenReturn(principal);
        when(userService.getById(userId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.avatarUrl").isEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/sync-profile updates and returns refreshed user")
    void syncProfile_returnsUpdatedUserResponse() throws Exception {
        UUID userId = UUID.randomUUID();
        User updated = User.builder()
                .id(userId)
                .githubId(197362476L)
                .githubUsername("ManitGitUser")
                .displayName("Manit Updated")
                .avatarUrl("https://avatars.githubusercontent.com/u/197362476?v=4")
                .build();

        AppUserPrincipal principal = new AppUserPrincipal(updated, Map.of("id", 197362476L));
        when(currentUser.require()).thenReturn(principal);
        when(userService.syncProfile(userId)).thenReturn(updated);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/sync-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.githubId").value(197362476L))
                .andExpect(jsonPath("$.githubUsername").value("ManitGitUser"))
                .andExpect(jsonPath("$.displayName").value("Manit Updated"))
                .andExpect(jsonPath("$.avatarUrl").value("https://avatars.githubusercontent.com/u/197362476?v=4"));
    }
}
