package com.ahicode.bidflow.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Getter
@Component
public class JwtTestUtil {

    private final String secretKeyBase64;
    private final SecretKey secretKey;

    public JwtTestUtil(@Value("${spring.security.oauth2.resourceserver.jwt.secret-key}") String secretKeyBase64) {
        this.secretKeyBase64 = secretKeyBase64;
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateValidAccessToken(String userId, String role, String status) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString()) // jti
                .claims(Map.of(
                        "role", role,
                        "status", status
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))    // 900s = 15mins
                .signWith(secretKey, io.jsonwebtoken.SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractJti(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getId();
    }


}
