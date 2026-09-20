package com.example.gitbot.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoConfigTest {

    @Test
    void tokenEncryptor_encryptsAndDecryptsSuccessfully() {
        CryptoConfig config = new CryptoConfig();
        // Salt must be a valid hex-encoded string (at least 8 bytes -> 16 hex chars)
        String password = "test-secret-password";
        String salt = "1234567890abcdef";

        TextEncryptor encryptor = config.tokenEncryptor(password, salt);

        String original = "gho_testToken123456789!@#$%^&*()";
        String encrypted = encryptor.encrypt(original);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = encryptor.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }
}
