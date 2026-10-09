package com.ahicode.bidflow.auth.utils;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;

@Component
public class JwtKeyProvider {

    private static final int MIN_KEY_LENGTH_BYTES = 32;

    public Key getSignKey(String secretKeyBase64) {
        if (secretKeyBase64 == null || secretKeyBase64.isBlank()) {
            throw new IllegalStateException("JWT secret key cannot be empty");
        }

        byte[] keyBytes = Decoders.BASE64.decode(secretKeyBase64);
        if (keyBytes.length < MIN_KEY_LENGTH_BYTES) {
            throw new IllegalStateException("JWT secret key must be at least %d bytes (base64-encoded: %d chars)"
                    .formatted(MIN_KEY_LENGTH_BYTES, MIN_KEY_LENGTH_BYTES * 4 / 3)
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
