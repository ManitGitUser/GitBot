package com.example.gitbot.service.demo;

import com.example.gitbot.exception.BadRequestException;
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

@Service
public class DemoTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String tokenSecret;
    private final JsonMapper jsonMapper;

    public record DemoTokenPayload(
            String id,
            long exp,
            boolean demo
    ) {
    }

    public DemoTokenService(
            @Value("${app.demo.token-secret:gitbot-demo-secret-key-at-least-32-chars-long!}") String tokenSecret,
            @Autowired(required = false) JsonMapper jsonMapper
    ) {
        this.tokenSecret = tokenSecret;
        this.jsonMapper = jsonMapper != null ? jsonMapper : JsonMapper.builder().build();
    }

    /**
     * Generates a signed demo session token containing session ID, expiration, and demo flag.
     * The token does NOT contain the message count; Redis is authoritative for the count.
     */
    public String createToken(String sessionId, long exp) {
        DemoTokenPayload payload = new DemoTokenPayload(sessionId, exp, true);
        return signToken(payload);
    }

    /**
     * Parses and cryptographically verifies the incoming demo token.
     * Verifies HMAC signature, demo flag, and expiration.
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
