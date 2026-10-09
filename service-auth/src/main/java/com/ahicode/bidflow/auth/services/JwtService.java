package com.ahicode.bidflow.auth.services;

import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import io.jsonwebtoken.Claims;

import java.time.Duration;
import java.util.function.Function;

public interface JwtService {
    String generateRefreshToken(UserEntity user);
    String generateAccessToken(UserEntity user);
    <T> T extractClaims(String token, TokenType tokenType, Function<Claims, T> claimsResolver);
    Duration getRefreshTokenTtl();
    Duration getAccessTokenTtl();
}
