package com.example.gitbot.service.demo;

import com.example.gitbot.dto.DemoChatRequest;
import com.example.gitbot.entity.GitRepo;
import com.example.gitbot.enums.IndexStatus;
import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.TooManyRequestsException;
import com.example.gitbot.repository.ChatMessageRepository;
import com.example.gitbot.repository.ChatSessionRepository;
import com.example.gitbot.repository.GitRepoRepository;
import com.example.gitbot.repository.MessageReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DemoSessionRedisServiceTest {

    @Autowired
    private DemoSessionRedisService demoSessionRedisService;

    @Autowired
    private DemoTokenService demoTokenService;

    @Autowired
    private DemoRateLimiter demoRateLimiter;

    @Autowired
    private DemoChatService demoChatService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private MessageReportRepository messageReportRepository;

    @Autowired
    private GitRepoRepository gitRepoRepository;

    private final List<String> sessionKeysToClean = new ArrayList<>();
    private final List<String> rateKeysToClean = new ArrayList<>();
    private final List<String> quotaKeysToClean = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Ensure a demo repository exists with READY status for any service checks
        GitRepo demoRepo = gitRepoRepository.findFirstByIsDemoTrue().orElse(null);
        if (demoRepo == null) {
            demoRepo = GitRepo.builder()
                    .name("GitBot")
                    .fullName("ManitGitUser/GitBot")
                    .indexStatus(IndexStatus.READY)
                    .isDemo(true)
                    .build();
            gitRepoRepository.save(demoRepo);
        }
    }

    @AfterEach
    void tearDown() {
        for (String key : sessionKeysToClean) {
            redisTemplate.delete(key);
        }
        for (String key : rateKeysToClean) {
            redisTemplate.delete(key);
        }
        for (String key : quotaKeysToClean) {
            redisTemplate.delete(key);
        }
    }

    @Test
    @DisplayName("First request without token creates Redis session with count 1 and TTL")
    void firstRequest_createsRedisSession_withCount1AndTtl() {
        DemoSessionRedisService.DemoSessionResult result = demoSessionRedisService.getOrIncrementSession(null);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.currentMessageCount()).isEqualTo(1);
        assertThat(result.maxMessages()).isEqualTo(5);
        assertThat(result.token()).isNotBlank();

        String redisKey = "demo:session:" + result.sessionId();
        sessionKeysToClean.add(redisKey);

        Integer redisCount = demoSessionRedisService.getSessionCount(result.sessionId());
        assertThat(redisCount).isEqualTo(1);

        Long ttl = demoSessionRedisService.getSessionTtlSeconds(result.sessionId());
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(7200);

        // Verify token can be parsed
        DemoTokenService.DemoTokenPayload payload = demoTokenService.parseAndVerify(result.token());
        assertThat(payload.id()).isEqualTo(result.sessionId());
        assertThat(payload.demo()).isTrue();
    }

    @Test
    @DisplayName("Subsequent requests increment count in Redis through 5, and 6th is rejected with 429")
    void sequentialRequests_incrementCountUpTo5_andSixthRejected() {
        // 1st request
        DemoSessionRedisService.DemoSessionResult first = demoSessionRedisService.getOrIncrementSession(null);
        sessionKeysToClean.add("demo:session:" + first.sessionId());
        assertThat(first.currentMessageCount()).isEqualTo(1);

        String token = first.token();

        // 2nd through 5th requests
        for (int i = 2; i <= 5; i++) {
            DemoSessionRedisService.DemoSessionResult next = demoSessionRedisService.getOrIncrementSession(token);
            assertThat(next.currentMessageCount()).isEqualTo(i);
            assertThat(next.sessionId()).isEqualTo(first.sessionId());
            assertThat(demoSessionRedisService.getSessionCount(first.sessionId())).isEqualTo(i);
        }

        // 6th request must be rejected with TooManyRequestsException (HTTP 429)
        assertThatThrownBy(() -> demoSessionRedisService.getOrIncrementSession(token))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Demo limit reached");

        // Verify Redis count did not exceed 5
        assertThat(demoSessionRedisService.getSessionCount(first.sessionId())).isEqualTo(5);
    }

    @Test
    @DisplayName("Replay protection: old token cannot reset or restore previous message count")
    void replayProtection_oldTokenCannotResetCount() {
        // 1st request produces initial token
        DemoSessionRedisService.DemoSessionResult first = demoSessionRedisService.getOrIncrementSession(null);
        sessionKeysToClean.add("demo:session:" + first.sessionId());
        String initialToken = first.token();

        // Send 2nd, 3rd, 4th, 5th message
        demoSessionRedisService.getOrIncrementSession(initialToken); // 2
        demoSessionRedisService.getOrIncrementSession(initialToken); // 3
        demoSessionRedisService.getOrIncrementSession(initialToken); // 4
        demoSessionRedisService.getOrIncrementSession(initialToken); // 5

        assertThat(demoSessionRedisService.getSessionCount(first.sessionId())).isEqualTo(5);

        // Client presents the initial token that was issued at count 1
        assertThatThrownBy(() -> demoSessionRedisService.getOrIncrementSession(initialToken))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Demo limit reached");

        // Redis is authoritative: count remains 5
        assertThat(demoSessionRedisService.getSessionCount(first.sessionId())).isEqualTo(5);
    }

    @Test
    @DisplayName("Missing or expired Redis session is rejected with BadRequestException")
    void missingOrExpiredRedisSession_isRejected() {
        String nonExistentSessionId = UUID.randomUUID().toString();
        long exp = System.currentTimeMillis() / 1000 + 3600;
        String validSignedTokenForMissingSession = demoTokenService.createToken(nonExistentSessionId, exp);

        // Key does not exist in Redis
        assertThatThrownBy(() -> demoSessionRedisService.getOrIncrementSession(validSignedTokenForMissingSession))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Demo session expired or not found");
    }

    @Test
    @DisplayName("Concurrency: concurrent requests cannot exceed 5 accepted messages")
    void concurrency_cannotExceedMaxMessages() throws Exception {
        // Start a session at count 3 (2 remaining before limit)
        DemoSessionRedisService.DemoSessionResult initial = demoSessionRedisService.getOrIncrementSession(null); // 1
        sessionKeysToClean.add("demo:session:" + initial.sessionId());
        demoSessionRedisService.getOrIncrementSession(initial.token()); // 2
        demoSessionRedisService.getOrIncrementSession(initial.token()); // 3

        String token = initial.token();
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    demoSessionRedisService.getOrIncrementSession(token);
                    successCount.incrementAndGet();
                } catch (TooManyRequestsException e) {
                    rejectedCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads concurrently
        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Since session started at count 3 and limit is 5, exactly 2 requests should succeed (counts 4 and 5)
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(rejectedCount.get()).isEqualTo(threadCount - 2);

        // Final count in Redis must be exactly 5
        assertThat(demoSessionRedisService.getSessionCount(initial.sessionId())).isEqualTo(5);
    }

    @Test
    @DisplayName("Redis per-IP rate limiter enforces 20 requests per minute and blocks excess")
    void rateLimiter_enforcesLimitInRedis() {
        String testIp = "198.51.100.42";
        String rateKey = "demo:rate:" + testIp;
        rateKeysToClean.add(rateKey);

        // First 20 requests should pass
        for (int i = 1; i <= 20; i++) {
            demoRateLimiter.checkRateLimit(testIp);
        }

        // 21st request must be rejected
        assertThatThrownBy(() -> demoRateLimiter.checkRateLimit(testIp))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Rate limit exceeded");

        // Verify TTL is active on the rate key
        Long ttl = redisTemplate.getExpire(rateKey);
        assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("Zero persistence: demo requests create zero chat_sessions, chat_messages, or reports")
    void zeroPersistence_noDatabaseRecordsCreated() {
        long initialSessions = chatSessionRepository.count();
        long initialMessages = chatMessageRepository.count();
        long initialReports = messageReportRepository.count();

        // Perform demo session creation and message processing
        DemoSessionRedisService.DemoSessionResult session = demoSessionRedisService.getOrIncrementSession(null);
        sessionKeysToClean.add("demo:session:" + session.sessionId());

        demoSessionRedisService.getOrIncrementSession(session.token());

        // Invariant: PostgreSQL tables must remain completely untouched
        assertThat(chatSessionRepository.count()).isEqualTo(initialSessions);
        assertThat(chatMessageRepository.count()).isEqualTo(initialMessages);
        assertThat(messageReportRepository.count()).isEqualTo(initialReports);
    }

    @Test
    @DisplayName("Clearing site data does not reset message count: enforced by IP in Redis")
    void clearingSiteData_doesNotResetMessageCount_enforcedByIp() {
        String testIp = "192.0.2.99";
        quotaKeysToClean.add("demo:quota:" + testIp);

        // Message 1 from testIp (initial load)
        DemoSessionRedisService.DemoSessionResult msg1 = demoSessionRedisService.getOrIncrementSession(null, testIp);
        sessionKeysToClean.add("demo:session:" + msg1.sessionId());
        assertThat(msg1.currentMessageCount()).isEqualTo(1);
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(4);

        // Message 2 from testIp with token
        DemoSessionRedisService.DemoSessionResult msg2 = demoSessionRedisService.getOrIncrementSession(msg1.token(), testIp);
        assertThat(msg2.currentMessageCount()).isEqualTo(2);
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(3);

        // User clears site data in browser (token becomes null), sends Message 3
        DemoSessionRedisService.DemoSessionResult msg3 = demoSessionRedisService.getOrIncrementSession(null, testIp);
        sessionKeysToClean.add("demo:session:" + msg3.sessionId());
        // Must NOT reset to 1! Must be count 3!
        assertThat(msg3.currentMessageCount()).isEqualTo(3);
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(2);

        // Send messages 4 and 5
        DemoSessionRedisService.DemoSessionResult msg4 = demoSessionRedisService.getOrIncrementSession(msg3.token(), testIp);
        assertThat(msg4.currentMessageCount()).isEqualTo(4);

        DemoSessionRedisService.DemoSessionResult msg5 = demoSessionRedisService.getOrIncrementSession(msg4.token(), testIp);
        assertThat(msg5.currentMessageCount()).isEqualTo(5);
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(0);

        // User clears site data AGAIN after exhausting all 5 messages
        // Must be rejected with TooManyRequestsException even though token is null!
        assertThatThrownBy(() -> demoSessionRedisService.getOrIncrementSession(null, testIp))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Demo limit reached");

        // Remaining messages must remain 0
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(0);
    }

    @Test
    @DisplayName("Expired or missing session token rolls into fresh session if IP quota is available")
    void expiredSessionToken_rollsIntoFreshSession_ifIpQuotaAvailable() {
        String testIp = "192.0.2.150";
        quotaKeysToClean.add("demo:quota:" + testIp);

        // 1. Initial request from testIp
        DemoSessionRedisService.DemoSessionResult msg1 = demoSessionRedisService.getOrIncrementSession(null, testIp);
        sessionKeysToClean.add("demo:session:" + msg1.sessionId());
        assertThat(msg1.currentMessageCount()).isEqualTo(1);

        // 2. Simulate expired token from 6 hours ago
        String expiredSessionId = UUID.randomUUID().toString();
        long expiredTimestamp = Instant.now().getEpochSecond() - 21600; // 6 hours ago
        String staleToken = demoTokenService.createToken(expiredSessionId, expiredTimestamp);

        // 3. User presents staleToken with testIp (which still has 4 messages remaining)
        // Must succeed and seamlessly issue a fresh session token!
        DemoSessionRedisService.DemoSessionResult msg2 = demoSessionRedisService.getOrIncrementSession(staleToken, testIp);
        sessionKeysToClean.add("demo:session:" + msg2.sessionId());

        assertThat(msg2.currentMessageCount()).isEqualTo(2);
        assertThat(msg2.token()).isNotEqualTo(staleToken);
        assertThat(msg2.sessionId()).isNotEqualTo(expiredSessionId);
        assertThat(demoSessionRedisService.getIpRemainingMessages(testIp)).isEqualTo(3);
    }
}
