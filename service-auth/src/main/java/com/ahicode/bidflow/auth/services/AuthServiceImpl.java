package com.ahicode.bidflow.auth.services;

import com.ahicode.bidflow.auth.dtos.AuthResponse;
import com.ahicode.bidflow.auth.dtos.LoginRequest;
import com.ahicode.bidflow.auth.dtos.RegisterRequest;
import com.ahicode.bidflow.auth.dtos.UserProfile;
import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.exceptions.EmailAlreadyExistsException;
import com.ahicode.bidflow.auth.exceptions.InvalidCredentialException;
import com.ahicode.bidflow.auth.exceptions.UserBlockedException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final RefreshTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository repository;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        isEmailUnique(request.email());

        UserEntity user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.password()));

        user = repository.save(user);
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
        UserEntity user = repository.findByEmail(request.email())
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
    public AuthResponse refreshAccessToken(String refreshToken) {
        return null;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {

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
        if (repository.existsByEmail(email)) {
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
