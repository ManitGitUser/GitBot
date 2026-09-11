package com.example.gitbot.entity;

import jakarta.persistence.*;
import lombok.*;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

@Table(name="users")
@Entity
// @Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "github_id", unique = true, nullable = false)
    private Long githubId;

    @Column(name = "github_username", nullable = false, length = 100)
    private String githubUsername;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "access_token",  nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_scopes", length = 100)
    private String tokenScopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
