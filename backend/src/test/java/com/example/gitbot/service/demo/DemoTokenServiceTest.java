package com.example.gitbot.service.demo;

import com.example.gitbot.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTokenServiceTest {

    private DemoTokenService demoTokenService;
    private final String secret = "test-secret-key-32-chars-long-at-least!";

    @BeforeEach
    void setUp() {
        demoTokenService = new DemoTokenService(secret, JsonMapper.builder().build());
    }

    @Test
    void createToken_and_parseAndVerify_validToken() {
        String sessionId = UUID.randomUUID().toString();
        long exp = Instant.now().getEpochSecond() + 3600;

        String token = demoTokenService.createToken(sessionId, exp);
        assertThat(token).isNotBlank();

        DemoTokenService.DemoTokenPayload payload = demoTokenService.parseAndVerify(token);
        assertThat(payload.id()).isEqualTo(sessionId);
        assertThat(payload.exp()).isEqualTo(exp);
        assertThat(payload.demo()).isTrue();
    }

    @Test
    void parseAndVerify_tamperedSignature_throwsBadRequestException() {
        String sessionId = UUID.randomUUID().toString();
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = demoTokenService.createToken(sessionId, exp);

        // Alter signature
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> demoTokenService.parseAndVerify(tampered))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid demo token signature");
    }

    @Test
    void parseAndVerify_tamperedPayload_throwsBadRequestException() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = demoTokenService.createToken(sessionId, exp);
        String[] parts = token.split("\\.");

        // Tamper with payload (change session id)
        DemoTokenService.DemoTokenPayload forged = new DemoTokenService.DemoTokenPayload(
                UUID.randomUUID().toString(), exp, true
        );
        String forgedPayloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(JsonMapper.builder().build().writeValueAsBytes(forged));

        String forgedToken = forgedPayloadBase64 + "." + parts[1];

        assertThatThrownBy(() -> demoTokenService.parseAndVerify(forgedToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid demo token signature");
    }

    @Test
    void parseAndVerify_malformedToken_throwsBadRequestException() {
        assertThatThrownBy(() -> demoTokenService.parseAndVerify("not-a-token"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Malformed demo token");
    }

    @Test
    void parseAndVerify_expiredToken_throwsBadRequestException() {
        String sessionId = UUID.randomUUID().toString();
        long pastExp = Instant.now().getEpochSecond() - 60; // 1 min ago
        String expiredToken = demoTokenService.createToken(sessionId, pastExp);

        assertThatThrownBy(() -> demoTokenService.parseAndVerify(expiredToken))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Demo token expired");
    }

    @Test
    void parseAndVerify_missingDemoFlag_throwsBadRequestException() throws Exception {
        record BadPayload(String id, long exp, boolean demo) {}
        BadPayload bad = new BadPayload(UUID.randomUUID().toString(), Instant.now().getEpochSecond() + 3600, false);
        byte[] jsonBytes = JsonMapper.builder().build().writeValueAsBytes(bad);
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);

        // Valid signature on non-demo payload
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256"));
        byte[] sig = mac.doFinal(payloadBase64.getBytes());
        String token = payloadBase64 + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);

        assertThatThrownBy(() -> demoTokenService.parseAndVerify(token))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("demo flag missing");
    }
}
