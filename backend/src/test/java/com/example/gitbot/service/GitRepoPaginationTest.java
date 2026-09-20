package com.example.gitbot.service;

import com.example.gitbot.dto.GitRepoResponse;
import com.example.gitbot.dto.PageResponse;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.repository.GitRepoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitRepoPaginationTest {

    @Mock
    private GitRepoRepository gitRepoRepository;

    @InjectMocks
    private GitRepoService gitRepoService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Page 0 returns requested batch of 10 repositories and page metadata")
    void page0_returns10Repos_withMetadata() {
        List<GitRepo> repos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            repos.add(GitRepo.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .fullName("user/repo-" + String.format("%02d", i))
                    .owner("user")
                    .name("repo-" + i)
                    .defaultBranch("main")
                    .build());
        }

        Pageable pageable = PageRequest.of(0, 10);
        when(gitRepoRepository.findWithFilters(eq(userId), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(repos, pageable, 25));

        PageResponse<GitRepoResponse> result = gitRepoService.listStored(userId, null, null, null, pageable);

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(25);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();

        verify(gitRepoRepository).findWithFilters(userId, null, null, null, pageable);
    }

    @Test
    @DisplayName("Page 1 returns next page with correct hasPrevious and hasNext")
    void page1_returnsNextPage() {
        List<GitRepo> repos = new ArrayList<>();
        for (int i = 10; i < 20; i++) {
            repos.add(GitRepo.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .fullName("user/repo-" + i)
                    .owner("user")
                    .name("repo-" + i)
                    .defaultBranch("main")
                    .build());
        }

        Pageable pageable = PageRequest.of(1, 10);
        when(gitRepoRepository.findWithFilters(eq(userId), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(repos, pageable, 25));

        PageResponse<GitRepoResponse> result = gitRepoService.listStored(userId, null, null, null, pageable);

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
    }
}
