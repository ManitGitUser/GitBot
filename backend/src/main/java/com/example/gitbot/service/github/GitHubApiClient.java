package com.example.gitbot.service.github;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GitHubApiClient {

    private static final String API_BASE = "https://api.github.com";

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP = new ParameterizedTypeReference<List<Map<String, Object>>>() {
    };

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<Map<String, Object>>() {
    };

    private final RestClient.Builder restClientBuilder;

    public List<Map<String, Object>> listUserRepos(String accessToken) {
        List<Map<String, Object>> all = new ArrayList<>();
        int page = 1;
        while (page <= 10) {
            final int currentPage = page;
            List<Map<String, Object>> pageRepos = client(accessToken)
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("affiliation", "owner,collaborator,organization_member")
                            .queryParam("sort", "updated")
                            .queryParam("per_page", 100)
                            .queryParam("page", currentPage)
                            .build()
                    )
                    .retrieve()
                    .body(LIST_MAP);
            if (pageRepos == null || pageRepos.isEmpty()) {
                break;
            }
            all.addAll(pageRepos);
            if (pageRepos.size() < 100) {
                break;
            }
            page++;
        }
        return all;
    }

    public String getLatestCommitSha(String accessToken, String owner, String repo, String branch) {
        Map<String, Object> body = client(accessToken)
                .get()
                .uri("/repos/{owner}/{repo}/commits/{ref}", owner, repo, branch)
                .retrieve()
                .body(MAP);
        if (body == null || body.get("sha") == null) {
            throw new IllegalStateException("Failed to retrieve latest commit SHA for " + owner + "/" + repo);
        }
        return String.valueOf(body.get("sha"));
    }

    public Map<String, Object> getRepoTree(String accessToken, String owner, String repo, String branch) {
        return client(accessToken)
                .get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", owner, repo, branch)
                .retrieve()
                .body(MAP);
    }

    public String getFileContent(String accessToken, String owner, String repo, String path, String ref) {
        String cleanPath = path != null && path.startsWith("/") ? path.substring(1) : (path != null ? path : "");
        Map<String, Object> body = client(accessToken)
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/repos/{owner}/{repo}/contents/" + cleanPath);
                    if (ref != null && !ref.isBlank()) {
                        builder.queryParam("ref", ref);
                    }
                    return builder.build(owner, repo);
                })
                .retrieve()
                .body(MAP);
        if (body == null) {
            return null;
        }
        Object encoding = body.get("encoding");
        Object content = body.get("content");
        if (content == null) {
            return null;
        }
        if ("base64".equals(String.valueOf(encoding))) {
            String raw = String.valueOf(content).replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        }
        return String.valueOf(content);
    }

    public String getFileContent(String accessToken, String owner, String repo, String path) {
        return getFileContent(accessToken, owner, repo, path, null);
    }

    public Map<String, Object> getCurrentUserProfile(String accessToken) {
        return client(accessToken)
                .get()
                .uri("/user")
                .retrieve()
                .body(MAP);
    }

    private RestClient client(String accessToken) {
        return restClientBuilder
                .baseUrl(API_BASE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "GitBot")
                .build();
    }
}
