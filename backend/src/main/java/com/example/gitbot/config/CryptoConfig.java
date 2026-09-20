package com.example.gitbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
public class CryptoConfig {

    @Bean
    TextEncryptor tokenEncryptor(
            @Value("${app.token-encryptor-password}") String password,
            @Value("${app.token-encryptor-salt}") String salt) {
        BytesEncryptor encryptor = AesGcmBytesEncryptor.withPassword(password, salt).build();
        return new TextEncryptor() {
            @Override
            public String encrypt(String text) {
                return new String(Hex.encode(encryptor.encrypt(Utf8.encode(text))));
            }

            @Override
            public String decrypt(String encryptedText) {
                return Utf8.decode(encryptor.decrypt(Hex.decode(encryptedText)));
            }
        };
    }
}
