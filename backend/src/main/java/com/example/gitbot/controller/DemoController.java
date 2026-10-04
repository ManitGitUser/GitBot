package com.example.gitbot.controller;

import com.example.gitbot.dto.DemoChatRequest;
import com.example.gitbot.dto.DemoStatusResponse;
import com.example.gitbot.service.demo.DemoChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoChatService demoChatService;

    @GetMapping("/status")
    public ResponseEntity<DemoStatusResponse> getDemoStatus(HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        return ResponseEntity.ok(demoChatService.getDemoStatus(clientIp));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter demoChat(
            @Valid @RequestBody DemoChatRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String clientIp = extractClientIp(httpRequest);
        return demoChatService.streamDemoChat(request, clientIp, httpResponse);
    }

    private String extractClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
