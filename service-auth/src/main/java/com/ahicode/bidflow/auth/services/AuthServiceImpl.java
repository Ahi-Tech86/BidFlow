package com.ahicode.bidflow.auth.services;

import com.ahicode.bidflow.auth.dtos.AuthResponse;
import com.ahicode.bidflow.auth.dtos.LoginRequest;
import com.ahicode.bidflow.auth.dtos.RegisterRequest;
import com.ahicode.bidflow.auth.dtos.UserProfile;
import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.exceptions.*;
import com.ahicode.bidflow.auth.mappers.UserMapper;
import com.ahicode.bidflow.auth.repositories.RefreshTokenRepository;
import com.ahicode.bidflow.auth.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RefreshTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        isEmailUnique(request.email());

        UserEntity user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));

        user = userRepository.save(user);
        log.info("[AUTH] Successful registered user: {}", user.getEmail());

        String refreshToken = jwtService.generateRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenJti = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getId);

        saveRefreshToken(user, refreshToken, refreshTokenJti);

        return createAuthResponse(refreshToken, accessToken, user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialException("Wrong email or password"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserBlockedException("Your account has been blocked, please contact support");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialException("Wrong email or password");
        }

        String refreshToken = jwtService.generateRefreshToken(user);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenJti = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getId);

        saveRefreshToken(user, refreshToken, refreshTokenJti);

        AuthResponse response = createAuthResponse(refreshToken, accessToken, user);

        log.info("[AUTH] Successful login for user: {}", user.getEmail());

        return response;
    }

    @Override
    @Transactional
    public AuthResponse refreshAccessToken(String refreshToken) {
        // Extract token JTI and user id
        String jti = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getId);
        String userIdStr = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getSubject);

        // Searching token in DB
        RefreshTokenEntity storedToken = tokenRepository.findByJti(jti)
                .orElseThrow(() -> new InvalidTokenException("Invalid or unknown refresh token."));

        // Family revocation
        if (storedToken.isRevoked()) {
            log.error("[SECURITY] Revoked token reuse detected! User ID: {}", userIdStr);
            tokenRepository.revokeAllByUserId(storedToken.getUserId());
            throw new InvalidTokenException("Token has been revoked, please log in again.");
        }

        // Checking expiration date
        if (storedToken.getExpiresAt().isBefore(ZonedDateTime.now())) {
            throw new TokenExpiredException("Refresh token has expired. Please log in again.");
        }

        // Checking user existence and status
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in token subject: {}", userIdStr);
            throw new InvalidTokenException("Invalid token format.");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User associated with this token no longer exists."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            tokenRepository.revokeAllByUserId(userId);
            throw new UserBlockedException("Your account has been suspended or blocked, please contact support");
        }

        // Token rotation: revoke old token
        storedToken.setRevoked(true);
        tokenRepository.save(storedToken);

        // Generating new token pair
        String newRefreshToken = jwtService.generateRefreshToken(user);
        String newAccessToken = jwtService.generateAccessToken(user);

        // Saving new refresh token in DB
        saveRefreshToken(user, newRefreshToken, jwtService.extractClaims(newRefreshToken, TokenType.REFRESH_TOKEN, Claims::getId));

        log.info("[AUTH] Access token refreshed successfully for user: {}", userIdStr);
        return createAuthResponse(newRefreshToken, newAccessToken, user);
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // Revoke refresh token
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                String jti = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getId);
                String userId = jwtService.extractClaims(refreshToken, TokenType.REFRESH_TOKEN, Claims::getSubject);

                tokenRepository.findByJti(jti).ifPresent(tokenEntity -> {
                    if (!tokenEntity.isRevoked()) {
                        tokenEntity.setRevoked(true);
                        tokenRepository.save(tokenEntity);
                        log.info("[AUTH] Refresh token revoked for user: {}", userId);
                    }
                });
            } catch (Exception e) {
                log.warn("Could not revoke refresh token during logout (token may be already invalid/expired): {}", e.getMessage());
            }
        }

        // TODO: add access token in blacklist
    }

    private AuthResponse createAuthResponse(String refreshToken, String accessToken, UserEntity user) {
        UserProfile profile = userMapper.toProfile(user);

        return AuthResponse.builder()
                .refreshToken(refreshToken)
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenTtl().toSeconds())
                .userInfo(profile)
                .build();
    }

    private void isEmailUnique(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("User with email " + email + " already exists");
        }
    }

    private void saveRefreshToken(UserEntity user, String refreshToken, String jti) {
        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .userId(user.getId())
                .jti(jti)
                .token(refreshToken)
                .expiresAt(ZonedDateTime.now().plus(jwtService.getRefreshTokenTtl()))
                .revoked(false)
                .build();

        tokenRepository.save(token);
        log.info("[AUTH] Refresh token saved for user: {}", user.getEmail());
    }
}
