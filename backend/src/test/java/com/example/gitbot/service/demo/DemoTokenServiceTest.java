package com.example.gitbot.service.demo;

import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTokenServiceTest {

    private DemoTokenService demoTokenService;
    private final String secret = "test-secret-key-32-chars-long-at-least!";

    @BeforeEach
    void setUp() {
        demoTokenService = new DemoTokenService(secret, 5, 3600, JsonMapper.builder().build());
    }

    @Test
    void validateAndIncrement_nullOrBlankToken_issuesCount1() {
        DemoTokenService.TokenResult result = demoTokenService.validateAndIncrement(null);

        assertThat(result.currentMessageNumber()).isEqualTo(1);
        assertThat(result.maxMessages()).isEqualTo(5);
        assertThat(result.sessionId()).isNotNull();
        assertThat(result.nextToken()).isNotBlank();

        DemoTokenService.DemoTokenPayload payload = demoTokenService.parseAndVerify(result.nextToken());
        assertThat(payload.count()).isEqualTo(1);
        assertThat(payload.demo()).isTrue();
        assertThat(payload.id()).isEqualTo(result.sessionId());
    }

    @Test
    void validateAndIncrement_sequentialCalls_incrementsUpTo5() {
        String token = null;
        for (int i = 1; i <= 4; i++) {
            DemoTokenService.TokenResult result = demoTokenService.validateAndIncrement(token);
            assertThat(result.currentMessageNumber()).isEqualTo(i);
            token = result.nextToken();
        }

        // 5th message
        DemoTokenService.TokenResult fifth = demoTokenService.validateAndIncrement(token);
        assertThat(fifth.currentMessageNumber()).isEqualTo(5);

        // 6th message with token having count=5 must be rejected
        assertThatThrownBy(() -> demoTokenService.validateAndIncrement(fifth.nextToken()))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Demo limit reached");
    }

    @Test
    void validateAndIncrement_tamperedSignature_throwsBadRequestException() {
        DemoTokenService.TokenResult result = demoTokenService.validateAndIncrement(null);
        String token = result.nextToken();

        // Alter signature
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> demoTokenService.validateAndIncrement(tampered))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid demo token signature");
    }

    @Test
    void validateAndIncrement_tamperedPayloadCount_throwsBadRequestException() throws Exception {
        DemoTokenService.TokenResult result = demoTokenService.validateAndIncrement(null);
        String[] parts = result.nextToken().split("\\.");

        // Forge payload with count = 0
        DemoTokenService.DemoTokenPayload forged = new DemoTokenService.DemoTokenPayload(
                result.sessionId(), 0, Instant.now().getEpochSecond() + 3600, true
        );
        String forgedPayloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(JsonMapper.builder().build().writeValueAsBytes(forged));

        String forgedToken = forgedPayloadBase64 + "." + parts[1];

        assertThatThrownBy(() -> demoTokenService.validateAndIncrement(forgedToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid demo token signature");
    }

    @Test
    void validateAndIncrement_malformedToken_throwsBadRequestException() {
        assertThatThrownBy(() -> demoTokenService.validateAndIncrement("not-a-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Malformed demo token");
    }

    @Test
    void validateAndIncrement_expiredToken_throwsBadRequestException() {
        // Create service with -10 second TTL
        DemoTokenService expiredService = new DemoTokenService(secret, 5, -10, JsonMapper.builder().build());
        DemoTokenService.TokenResult result = expiredService.validateAndIncrement(null);

        assertThatThrownBy(() -> expiredService.validateAndIncrement(result.nextToken()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Demo token expired");
    }
}
