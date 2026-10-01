package com.ahicode.bidflow.auth.services;

import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import com.ahicode.bidflow.auth.utils.JwtKeyProvider;
import com.ahicode.bidflow.auth.utils.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtProperties jwtProperties;
    private final JwtKeyProvider keyProvider;

    private Key refreshTokenSignKey;
    private Key accessTokenSignKey;
    private Duration refreshTokenTtl;
    private Duration accessTokenTtl;

    @PostConstruct
    public void init() {
        this.refreshTokenSignKey = keyProvider.getSignKey(jwtProperties.getRefreshToken().getSecretKey());
        this.accessTokenSignKey = keyProvider.getSignKey(jwtProperties.getAccessToken().getSecretKey());
        this.refreshTokenTtl = jwtProperties.getRefreshToken().getExpiration();
        this.accessTokenTtl = jwtProperties.getAccessToken().getExpiration();
    }

    @Override
    public String generateRefreshToken(UserEntity user) {
        return generateToken(user, TokenType.REFRESH_TOKEN);
    }

    @Override
    public String generateAccessToken(UserEntity user) {
        return generateToken(user, TokenType.ACCESS_TOKEN);
    }

    @Override
    public <T> T extractClaims(String token, TokenType tokenType, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token, tokenType);
        return claimsResolver.apply(claims);
    }

    @Override
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    @Override
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    private Claims extractAllClaims(String token, TokenType tokenType) {
        Key signKey = tokenType == TokenType.REFRESH_TOKEN ? refreshTokenSignKey : accessTokenSignKey;
        return Jwts.parser()
                .verifyWith((SecretKey) signKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String generateToken(UserEntity user, TokenType tokenType) {
        Key signKey = tokenType == TokenType.REFRESH_TOKEN ? refreshTokenSignKey : accessTokenSignKey;
        Duration ttl = tokenType == TokenType.REFRESH_TOKEN ? refreshTokenTtl : accessTokenTtl;
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("status", user.getStatus().name());

        return Jwts.builder()
                .subject(user.getId().toString())
                .id(jti)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }
}
