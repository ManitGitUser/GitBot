package com.example.gitbot.service.demo;

import com.example.gitbot.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Shared per-IP rate limiter backed by Redis.
 *
 * <p>
 * Key format: demo:rate:{sanitizedIp}
 * Window: 60 seconds
 * Limit: 20 requests/minute/IP (configurable)
 */
@Component
public class DemoRateLimiter {

    private static final String RATE_KEY_PREFIX = "demo:rate:";
    private static final long WINDOW_SECONDS = 60L;

    private static final String RATE_LIMIT_LUA = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
                return 0
            end
            return 1
            """;

    private final RedisScript<Long> rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    private final StringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public DemoRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.demo.ip-rate-limit-per-minute:20}") int maxRequestsPerMinute) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    /**
     * Checks if the client IP has exceeded the allowed requests in the sliding
     * 60-second window.
     * Throws TooManyRequestsException if exceeded.
     */
    public void checkRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }

        String sanitizedIp = sanitizeIp(clientIp);
        String key = RATE_KEY_PREFIX + sanitizedIp;

        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(maxRequestsPerMinute),
                String.valueOf(WINDOW_SECONDS));

        if (result != null && result == 0L) {
            throw new TooManyRequestsException(
                    "Rate limit exceeded. Please wait a moment before sending another message.");
        }
    }

    private String sanitizeIp(String rawIp) {
        String trimmed = rawIp.trim();
        // Remove any characters other than alphanumeric, dots, colons, and hyphens to
        // prevent key injection
        String cleaned = trimmed.replaceAll("[^a-zA-Z0-9.:-]", "_");
        return cleaned.length() > 45 ? cleaned.substring(0, 45) : cleaned;
    }
}
