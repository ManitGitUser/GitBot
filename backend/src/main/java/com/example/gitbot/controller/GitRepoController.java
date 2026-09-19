package com.example.gitbot.controller;

import com.example.gitbot.dto.GitRepoResponse;
import com.example.gitbot.dto.IndexStatusResponse;
import com.example.gitbot.dto.SyncAllReposResponse;
import com.example.gitbot.dto.SyncRepoResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.security.CurrentUser;
import com.example.gitbot.service.GitRepoService;
import com.example.gitbot.service.indexing.IndexingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/repos")
@RequiredArgsConstructor
public class GitRepoController {

    private final GitRepoService gitRepoService;
    private final CurrentUser currentUser;
    private final IndexingService indexingService;

    @GetMapping
    public com.example.gitbot.dto.PageResponse<GitRepoResponse> listAll(
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "search", required = false) String search
    ) {
        UUID userId = currentUser.require().getId();
        if (refresh) {
            gitRepoService.syncAndListGitRepos(userId);
        }
        return gitRepoService.listStored(userId, status, visibility, search, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public GitRepoResponse get(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return gitRepoService.toResponse(gitRepoService.requireOwned(id, userId));
    }

    @GetMapping("/{id}/status")
    public IndexStatusResponse status(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        return gitRepoService.status(userId, id);
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<GitRepoResponse> index(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        GitRepo repo = indexingService.startIndexing(id, userId);
        indexingService.indexAsync(id, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(gitRepoService.toResponse(repo));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<SyncRepoResponse> sync(@PathVariable UUID id) {
        UUID userId = currentUser.require().getId();
        SyncRepoResponse response = gitRepoService.syncRepo(id, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/sync-all")
    public ResponseEntity<SyncAllReposResponse> syncAll() {
        UUID userId = currentUser.require().getId();
        SyncAllReposResponse response = gitRepoService.syncAllRepos(userId);
        return ResponseEntity.ok(response);
    }
}
