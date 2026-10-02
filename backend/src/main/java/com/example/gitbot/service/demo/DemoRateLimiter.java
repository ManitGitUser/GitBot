package com.example.gitbot.service.demo;

import com.example.gitbot.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DemoRateLimiter {

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> requestWindows = new ConcurrentHashMap<>();

    public DemoRateLimiter(
            @Value("${app.demo.ip-rate-limit-per-minute:20}") int maxRequestsPerMinute
    ) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Checks if the client IP has exceeded the allowed requests in the last 60 seconds.
     * Throws TooManyRequestsException if exceeded.
     */
    public void checkRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }

        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;

        // Cleanup if map gets large
        if (requestWindows.size() > 5000) {
            requestWindows.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || entry.getValue().peekLast() < windowStart;
                }
            });
        }

        Deque<Long> timestamps = requestWindows.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequestsPerMinute) {
                throw new TooManyRequestsException("Rate limit exceeded. Please wait a moment before sending another message.");
            }

            timestamps.addLast(now);
        }
    }
}
