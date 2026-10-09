package com.ahicode.bidflow.auth.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class JwtKeyProviderTest {

    private JwtKeyProvider keyProvider;

    @BeforeEach
    void setUp() {
        keyProvider = new JwtKeyProvider();
    }

    @Test
    void getSignKey_ShouldReturnKey() {
        String validSecretKey = generateSecretKeyBase64();

        Key key = keyProvider.getSignKey(validSecretKey);

        assertNotNull(key);
        assertEquals("HmacSHA256", key.getAlgorithm());
    }

    @NullAndEmptySource
    @ParameterizedTest
    void getSignKey_ShouldThrowException_WhenSecretKeyIsNullOrBlank(String invalidKey) {
        assertThatThrownBy(() -> keyProvider.getSignKey(invalidKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getSignKey_ShouldThrowException_WhenResultKeyBytesIsLessThanMinKeyBytes() {
        String invalidSecretKey = generateSecretKeyBase64().substring(10);

        assertThatThrownBy(() -> keyProvider.getSignKey(invalidSecretKey))
                .isInstanceOf(IllegalStateException.class);
    }

    private String generateSecretKeyBase64() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}