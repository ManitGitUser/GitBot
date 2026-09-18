package com.example.gitbot.controller;

import com.example.gitbot.dto.PublicSharedChatResponse;
import com.example.gitbot.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, read-only endpoint for shared chats.
 * Permits access without authentication.
 */
@RestController
@RequestMapping("/api/public/shares")
@RequiredArgsConstructor
public class PublicShareController {

    private final ChatService chatService;

    @GetMapping("/{shareToken}")
    public ResponseEntity<PublicSharedChatResponse> getPublicShare(@PathVariable String shareToken) {
        return ResponseEntity.ok(chatService.getPublicShare(shareToken));
    }
}
