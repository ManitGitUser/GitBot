package com.example.gitbot.service.demo;

import com.example.gitbot.exception.BadRequestException;
import com.example.gitbot.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class DemoTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String tokenSecret;
    private final int maxMessages;
    private final long tokenTtlSeconds;
    private final JsonMapper jsonMapper;

    public record DemoTokenPayload(
            String id,
            int count,
            long exp,
            boolean demo
    ) {
    }

    public record TokenResult(
            String sessionId,
            int currentMessageNumber,
            int maxMessages,
            String nextToken
    ) {
    }

    public DemoTokenService(
            @Value("${app.demo.token-secret:gitbot-demo-secret-key-at-least-32-chars-long!}") String tokenSecret,
            @Value("${app.demo.max-messages:5}") int maxMessages,
            @Value("${app.demo.token-ttl-seconds:7200}") long tokenTtlSeconds,
            @Autowired(required = false) JsonMapper jsonMapper
    ) {
        this.tokenSecret = tokenSecret;
        this.maxMessages = maxMessages;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonMapper.builder().build();
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * Validates the incoming demo token and issues the next token with incremented count.
     * If rawToken is null or blank, initializes message #1.
     * If the token count has reached the maximum, throws TooManyRequestsException.
     */
    public TokenResult validateAndIncrement(String rawToken) {
        long now = Instant.now().getEpochSecond();
        long exp = now + tokenTtlSeconds;

        if (rawToken == null || rawToken.isBlank()) {
            // First message in demo conversation
            String sessionId = UUID.randomUUID().toString();
            int currentCount = 1;
            String nextToken = signToken(new DemoTokenPayload(sessionId, currentCount, exp, true));
            return new TokenResult(sessionId, currentCount, maxMessages, nextToken);
        }

        DemoTokenPayload payload = parseAndVerify(rawToken.trim());

        if (payload.count() >= maxMessages) {
            throw new TooManyRequestsException(
                    "Demo limit reached. You have used all " + maxMessages + " demo messages. Sign in with GitHub to continue using GitBot."
            );
        }

        int nextCount = payload.count() + 1;
        String nextToken = signToken(new DemoTokenPayload(payload.id(), nextCount, exp, true));
        return new TokenResult(payload.id(), nextCount, maxMessages, nextToken);
    }

    /**
     * Generates an initial token with 0 messages consumed (e.g. for page visit).
     */
    public String createInitialToken() {
        long exp = Instant.now().getEpochSecond() + tokenTtlSeconds;
        return signToken(new DemoTokenPayload(UUID.randomUUID().toString(), 0, exp, true));
    }

    /**
     * Parses and validates a demo token without incrementing count.
     */
    public DemoTokenPayload parseAndVerify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Demo token is missing.");
        }

        String[] parts = rawToken.split("\\.");
        if (parts.length != 2) {
            throw new BadRequestException("Malformed demo token.");
        }

        String payloadBase64 = parts[0];
        String signatureBase64 = parts[1];

        byte[] expectedSig = computeHmac(payloadBase64.getBytes(StandardCharsets.UTF_8));
        byte[] providedSig;
        try {
            providedSig = Base64.getUrlDecoder().decode(signatureBase64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Malformed demo token signature encoding.");
        }

        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            throw new BadRequestException("Invalid demo token signature.");
        }

        DemoTokenPayload payload;
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadBase64);
            payload = jsonMapper.readValue(payloadBytes, DemoTokenPayload.class);
        } catch (Exception e) {
            throw new BadRequestException("Failed to decode demo token payload.");
        }

        if (!payload.demo()) {
            throw new BadRequestException("Invalid demo token: demo flag missing.");
        }

        long now = Instant.now().getEpochSecond();
        if (now > payload.exp()) {
            throw new BadRequestException("Demo token expired. Please start a new demo session.");
        }

        return payload;
    }

    private String signToken(DemoTokenPayload payload) {
        try {
            byte[] jsonBytes = jsonMapper.writeValueAsBytes(payload);
            String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
            byte[] sig = computeHmac(payloadBase64.getBytes(StandardCharsets.UTF_8));
            String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            return payloadBase64 + "." + signatureBase64;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize and sign demo token", e);
        }
    }

    private byte[] computeHmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC SHA-256 calculation failed", e);
        }
    }
}
