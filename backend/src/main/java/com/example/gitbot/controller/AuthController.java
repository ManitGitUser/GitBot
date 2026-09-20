package com.example.gitbot.controller;

import com.example.gitbot.dto.UserResponse;
import com.example.gitbot.entity.User;
import com.example.gitbot.security.AppUserPrincipal;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final CurrentUser currUser;
    private final UserService userServ;

    @GetMapping("/login-url")
    public Map<String, String> loginUrl() {
        return Map.of("url", "/oauth2/authorization/github");
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        AppUserPrincipal principal = currUser.require();
        User user = userServ.getById(principal.getId());
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getGithubId(),
                user.getGithubUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        ));
    }

    @PostMapping("/sync-profile")
    public ResponseEntity<UserResponse> syncProfile() {
        AppUserPrincipal principal = currUser.require();
        User user = userServ.syncProfile(principal.getId());
        return ResponseEntity.ok(new UserResponse(
                user.getId(),
                user.getGithubId(),
                user.getGithubUsername(),
                user.getDisplayName(),
                user.getAvatarUrl()
        ));
    }

    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request, HttpServletResponse response) {
        AppUserPrincipal principal = currUser.require();
        userServ.deleteAccount(principal.getId());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Cookie cookie = new Cookie("GITBOT_SESSION", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        if (token == null) {
            return ResponseEntity.ok(Map.of());
        }
        return ResponseEntity.ok(Map.of(
                "token", token.getToken(),
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName()
        ));
    }
}
