package com.example.gitbot.service.demo;

import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

/**
 * Authoritative ephemeral demo session manager backed by Redis.
 *
 * <p>
 * Key format: demo:session:{sessionId}
 * Value: integer message count
 * TTL: configured demo TTL (default 2 hours)
 */
@Service
public class DemoSessionRedisService {

    private static final String SESSION_KEY_PREFIX = "demo:session:";
    private static final String QUOTA_KEY_PREFIX = "demo:quota:";

    /**
     * Atomic increment Lua script for session-only increments (fallback when no IP
     * supplied).
     *
     * <p>
     * Returns:
     * -1: Key does not exist (session missing or expired)
     * 0: Limit reached (current count >= maxMessages, key not modified)
     * >0: Successfully incremented next count
     */
    private static final String INCREMENT_LUA = """
            local current = redis.call('GET', KEYS[1])
            if not current then
                return -1
            end
            local count = tonumber(current)
            if count >= tonumber(ARGV[1]) then
                return 0
            end
            return redis.call('INCR', KEYS[1])
            """;

    /**
     * Atomic IP quota Lua script.
     *
     * <p>
     * Checks if the IP's total message count has reached maxMessages.
     * If reached, returns 0 (limit exceeded, not incremented).
     * Otherwise increments the IP key, sets TTL if new, and returns the updated
     * count.
     */
    private static final String IP_QUOTA_LUA = """
            local current = redis.call('GET', KEYS[1])
            if current and tonumber(current) >= tonumber(ARGV[1]) then
                return 0
            end
            local nextCount = redis.call('INCR', KEYS[1])
            local currentTtl = redis.call('TTL', KEYS[1])
            if currentTtl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return nextCount
            """;

    private final RedisScript<Long> incrementScript = new DefaultRedisScript<>(INCREMENT_LUA, Long.class);
    private final RedisScript<Long> ipQuotaScript = new DefaultRedisScript<>(IP_QUOTA_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final DemoTokenService demoTokenService;
    private final int maxMessages;
    private final long tokenTtlSeconds;

    public record DemoSessionResult(
            String sessionId,
            int currentMessageCount,
            int maxMessages,
            String token) {
    }

    public DemoSessionRedisService(
            StringRedisTemplate redisTemplate,
            DemoTokenService demoTokenService,
            @Value("${app.demo.max-messages:5}") int maxMessages,
            @Value("${app.demo.token-ttl-seconds:7200}") long tokenTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.demoTokenService = demoTokenService;
        this.maxMessages = maxMessages;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    /**
     * Resolves the demo session and atomically increments the message count in
     * Redis,
     * authoritative by client IP.
     */
    public DemoSessionResult getOrIncrementSession(String rawToken, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return getOrIncrementSessionWithoutIp(rawToken);
        }

        String sanitizedIp = sanitizeIp(clientIp);
        String ipKey = QUOTA_KEY_PREFIX + sanitizedIp;

        // 1. Authoritative check & increment against client IP in Redis
        Long ipResult = redisTemplate.execute(
                ipQuotaScript,
                Collections.singletonList(ipKey),
                String.valueOf(maxMessages),
                String.valueOf(tokenTtlSeconds));

        if (ipResult == null || ipResult == 0L) {
            throw new TooManyRequestsException(
                    "Demo limit reached. You have used all " + maxMessages
                            + " demo messages. Sign in with GitHub to continue using GitBot.");
        }

        int currentCount = ipResult.intValue();

        // 2. Manage session & HMAC token
        if (rawToken == null || rawToken.isBlank()) {
            // First message or cleared site data: create brand new Redis session tied to
            // current IP count
            String sessionId = UUID.randomUUID().toString();
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(sessionKey, String.valueOf(currentCount),
                    Duration.ofSeconds(tokenTtlSeconds));

            long exp = Instant.now().getEpochSecond() + tokenTtlSeconds;
            String token = demoTokenService.createToken(sessionId, exp);
            return new DemoSessionResult(sessionId, currentCount, maxMessages, token);
        }

        // Subsequent message with existing token: verify token and update session.
        // If token or session in Redis expired, seamlessly create a fresh session for
        // this IP
        String sessionId;
        String tokenToReturn;
        try {
            DemoTokenService.DemoTokenPayload payload = demoTokenService.parseAndVerify(rawToken.trim());
            String sessionKey = SESSION_KEY_PREFIX + payload.id();

            String existingSession = redisTemplate.opsForValue().get(sessionKey);

            if (existingSession != null) {
                sessionId = payload.id();
                tokenToReturn = rawToken;
            } else {
                sessionId = UUID.randomUUID().toString();
                long exp = Instant.now().getEpochSecond() + tokenTtlSeconds;
                tokenToReturn = demoTokenService.createToken(sessionId, exp);
            }
        } catch (Exception e) {
            sessionId = UUID.randomUUID().toString();
            long exp = Instant.now().getEpochSecond() + tokenTtlSeconds;
            tokenToReturn = demoTokenService.createToken(sessionId, exp);
        }

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(sessionKey, String.valueOf(currentCount), Duration.ofSeconds(tokenTtlSeconds));
        return new DemoSessionResult(sessionId, currentCount, maxMessages, tokenToReturn);
    }

    /**
     * Overload for callers without client IP (e.g. legacy tests).
     */
    public DemoSessionResult getOrIncrementSession(String rawToken) {
        return getOrIncrementSession(rawToken, null);
    }

    private DemoSessionResult getOrIncrementSessionWithoutIp(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            String sessionId = UUID.randomUUID().toString();
            String key = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(tokenTtlSeconds));

            long exp = Instant.now().getEpochSecond() + tokenTtlSeconds;
            String token = demoTokenService.createToken(sessionId, exp);
            return new DemoSessionResult(sessionId, 1, maxMessages, token);
        }

        DemoTokenService.DemoTokenPayload payload = demoTokenService.parseAndVerify(rawToken.trim());
        String key = SESSION_KEY_PREFIX + payload.id();

        Long result = redisTemplate.execute(
                incrementScript,
                Collections.singletonList(key),
                String.valueOf(maxMessages));

        if (result == null || result == -1L) {
            throw new BadRequestException(
                    "Demo session expired or not found. Please refresh the page to start a new demo.");
        }

        if (result == 0L) {
            throw new TooManyRequestsException(
                    "Demo limit reached. You have used all " + maxMessages
                            + " demo messages. Sign in with GitHub to continue using GitBot.");
        }

        return new DemoSessionResult(payload.id(), result.intValue(), maxMessages, rawToken);
    }

    /**
     * Returns remaining demo messages for a client IP from Redis.
     */
    public int getIpRemainingMessages(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return maxMessages;
        }
        String sanitizedIp = sanitizeIp(clientIp);
        String val = redisTemplate.opsForValue().get(QUOTA_KEY_PREFIX + sanitizedIp);
        if (val == null) {
            return maxMessages;
        }
        try {
            int used = Integer.parseInt(val.trim());
            return Math.max(0, maxMessages - used);
        } catch (NumberFormatException e) {
            return maxMessages;
        }
    }

    /**
     * Retrieves current message count recorded for an IP in Redis.
     */
    public Integer getIpQuotaCount(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        String val = redisTemplate.opsForValue().get(QUOTA_KEY_PREFIX + sanitizeIp(clientIp));
        return val != null ? Integer.parseInt(val.trim()) : null;
    }

    /**
     * Retrieves current message count from Redis without modifying it (e.g. for
     * testing / status).
     */
    public Integer getSessionCount(String sessionId) {
        String val = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId);
        return val != null ? Integer.parseInt(val) : null;
    }

    /**
     * Retrieves remaining TTL in seconds for a session.
     */
    public Long getSessionTtlSeconds(String sessionId) {
        return redisTemplate.getExpire(SESSION_KEY_PREFIX + sessionId);
    }

    /**
     * Sanitizes client IP string to prevent Redis key injection.
     */
    public String sanitizeIp(String rawIp) {
        if (rawIp == null) {
            return "unknown";
        }
        String trimmed = rawIp.trim();
        String cleaned = trimmed.replaceAll("[^a-zA-Z0-9.:-]", "_");
        return cleaned.length() > 45 ? cleaned.substring(0, 45) : cleaned;
    }
}
