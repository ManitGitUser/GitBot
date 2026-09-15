package com.example.gitbot.controller;

import com.example.gitbot.dto.GitRepoResponse;
import com.example.gitbot.dto.IndexStatusResponse;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.GitRepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class GitRepoController {

    private final GitRepoService gitRepoService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<GitRepoResponse> listAll(
            @RequestParam(name = "refresh", defaultValue = "true") boolean refresh
    ) {
        UUID userId = currentUser.require().getId();
        if (refresh) {
            return gitRepoService.syncAndListGitRepos(userId);
        }
        return gitRepoService.listStored(userId);
    }

    @GetMapping("/{id}")
    public GitRepoResponse get(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return gitRepoService.toResponse(gitRepoService.requireOwned(userId, id));
    }

    @GetMapping("/{id}/status")
    public IndexStatusResponse status(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return gitRepoService.status(userId, id);
    }
}
