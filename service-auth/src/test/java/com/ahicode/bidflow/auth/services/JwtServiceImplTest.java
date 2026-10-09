package com.ahicode.bidflow.auth.services;

import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.utils.JwtKeyProvider;
import com.ahicode.bidflow.auth.utils.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Key;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JwtServiceImplTest {

    private JwtServiceImpl jwtService;

    @Mock private JwtKeyProvider jwtKeyProvider;
    @Mock private JwtProperties jwtProperties;

    private Duration refreshTokenDuration;
    private Duration accessTokenDuration;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        refreshTokenDuration = Duration.ofDays(15);
        accessTokenDuration = Duration.ofMinutes(15);

        byte[] keyBytesForRefreshTokenSecret = new byte[32];
        byte[] keyBytesForAccessTokenSecret = new byte[32];
        new SecureRandom().nextBytes(keyBytesForRefreshTokenSecret);
        new SecureRandom().nextBytes(keyBytesForAccessTokenSecret);
        String secretKeyForRefreshToken = Base64.getEncoder().encodeToString(keyBytesForRefreshTokenSecret);
        String secretKeyForAccessToken = Base64.getEncoder().encodeToString(keyBytesForAccessTokenSecret);

        Key mockRefreshTokenKey = Keys.hmacShaKeyFor(secretKeyForRefreshToken.getBytes());
        Key mockAccessTokenKey = Keys.hmacShaKeyFor(secretKeyForAccessToken.getBytes());

        JwtProperties.RefreshTokenProperties refreshProp = mock(JwtProperties.RefreshTokenProperties.class);
        JwtProperties.AccessTokenProperties accessProp = mock(JwtProperties.AccessTokenProperties.class);

        when(jwtProperties.getRefreshToken()).thenReturn(refreshProp);
        when(jwtProperties.getAccessToken()).thenReturn(accessProp);

        when(refreshProp.getSecretKey()).thenReturn(secretKeyForRefreshToken);
        when(accessProp.getSecretKey()).thenReturn(secretKeyForAccessToken);

        when(refreshProp.getExpiration()).thenReturn(refreshTokenDuration);
        when(accessProp.getExpiration()).thenReturn(accessTokenDuration);

        when(jwtKeyProvider.getSignKey(secretKeyForRefreshToken)).thenReturn(mockRefreshTokenKey);
        when(jwtKeyProvider.getSignKey(secretKeyForAccessToken)).thenReturn(mockAccessTokenKey);

        jwtService = new JwtServiceImpl(jwtProperties, jwtKeyProvider);
        jwtService.init();

        user = UserEntity.builder()
                .id(UUID.randomUUID())
                .email("")
                .firstName("")
                .lastName("")
                .status(UserStatus.ACTIVE)
                .role(UserRole.ROLE_USER)
                .password("")
                .build();
    }

    @Test
    void generateRefreshToken_ReturnToken() {
        String token = jwtService.generateRefreshToken(user);

        String extractedJti = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getId);
        String extractedId = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getSubject);
        String extractedRole = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, claims ->
                claims.get("role", String.class));
        String extractedStatus = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, claims ->
                claims.get("status", String.class));
        Instant extractedIssued = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getIssuedAt).toInstant();
        Instant extractedExpiration = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getExpiration).toInstant();
        Duration actualTtl = Duration.between(extractedIssued, extractedExpiration);

        assertNotNull(token);
        assertThat(token).isNotEmpty();
        assertThat(token).isNotBlank();
        assertThat(extractedJti).isNotEmpty();
        assertThat(extractedJti).isNotBlank();
        assertEquals(extractedId, user.getId().toString());
        assertEquals(extractedRole, user.getRole().name());
        assertEquals(extractedStatus, user.getStatus().name());
        assertThat(actualTtl).isCloseTo(refreshTokenDuration, Duration.ofSeconds(2));
    }

    @Test
    void generateAccessToken_ReturnToken() {
        String token = jwtService.generateAccessToken(user);

        String extractedJti = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, Claims::getId);
        String extractedId = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, Claims::getSubject);
        String extractedRole = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, claims ->
                claims.get("role", String.class));
        String extractedStatus = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, claims ->
                claims.get("status", String.class));
        Instant extractedIssued = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, Claims::getIssuedAt).toInstant();
        Instant extractedExpiration = jwtService.extractClaims(token, TokenType.ACCESS_TOKEN, Claims::getExpiration).toInstant();
        Duration actualTtl = Duration.between(extractedIssued, extractedExpiration);

        assertNotNull(token);
        assertThat(token).isNotEmpty();
        assertThat(token).isNotBlank();
        assertThat(extractedJti).isNotEmpty();
        assertThat(extractedJti).isNotBlank();
        assertEquals(extractedId, user.getId().toString());
        assertEquals(extractedRole, user.getRole().name());
        assertEquals(extractedStatus, user.getStatus().name());
        assertThat(actualTtl).isCloseTo(accessTokenDuration, Duration.ofSeconds(2));
    }

    @Test
    void extractClaims_ShouldExtractStandardFields() {
        String token = jwtService.generateRefreshToken(user);

        String subject = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getSubject);
        assertEquals(user.getId().toString(), subject);

        String jti = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN, Claims::getId);
        assertNotNull(jti);
        assertFalse(jti.isEmpty());
    }

    @Test
    void extractClaims_ShouldExtractCustomClaims() {
        String token = jwtService.generateRefreshToken(user);

        String role = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN,
                claims -> claims.get("role", String.class));
        assertEquals(user.getRole().name(), role);

        String status = jwtService.extractClaims(token, TokenType.REFRESH_TOKEN,
                claims -> claims.get("status", String.class));
        assertEquals(user.getStatus().toString(), status);
    }

    @Test
    void extractClaims_ShouldThrowException_WhenTokenIsInvalid() {
        assertThrows(
                Exception.class,
                () -> jwtService
                        .extractClaims("invalid.token.string", TokenType.REFRESH_TOKEN, Claims::getSubject)
        );
    }
}