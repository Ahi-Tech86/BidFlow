package com.ahicode.bidflow.auth.service;

import com.ahicode.bidflow.auth.dtos.AuthResponse;
import com.ahicode.bidflow.auth.dtos.LoginRequest;
import com.ahicode.bidflow.auth.dtos.RegisterRequest;
import com.ahicode.bidflow.auth.dtos.UserProfile;
import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.TokenType;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.exceptions.*;
import com.ahicode.bidflow.auth.mappers.UserMapper;
import com.ahicode.bidflow.auth.repositories.RefreshTokenRepository;
import com.ahicode.bidflow.auth.repositories.UserRepository;
import com.ahicode.bidflow.auth.services.AuthServiceImpl;
import com.ahicode.bidflow.auth.services.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock private RefreshTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(tokenRepository, passwordEncoder, userRepository, userMapper, jwtService);
    }

    @Nested
    class Register {
        private final RegisterRequest request = new RegisterRequest(
                "test@mail.com",
                "Taylor",
                "Durden",
                "1234"
        );

        @Test
        void shouldRegisterUserSuccessfully_WhenEmailIsUnique() {
            UserEntity mappedEntity = new UserEntity();
            UserEntity savedEntity = new UserEntity();
            UUID savedUserId = UUID.randomUUID();
            savedEntity.setId(savedUserId);
            savedEntity.setEmail("test@mail.com");
            UserProfile profile = new UserProfile(
                    savedUserId,
                    "test@mail.com",
                    "Taylor",
                    "Durden",
                    UserRole.ROLE_USER,
                    UserStatus.ACTIVE
            );

            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userMapper.toEntity(request)).willReturn(mappedEntity);
            given(passwordEncoder.encode(anyString())).willReturn("encodedPassword");
            given(userRepository.save(any(UserEntity.class))).willReturn(savedEntity);
            given(jwtService.generateRefreshToken(savedEntity)).willReturn("random.refresh.token");
            given(jwtService.generateAccessToken(savedEntity)).willReturn("random.access.token");
            given(jwtService.getRefreshTokenTtl()).willReturn(Duration.ofDays(14));
            given(jwtService.getAccessTokenTtl()).willReturn(Duration.ofMinutes(15));
            given(userMapper.toProfile(any(UserEntity.class))).willReturn(profile);

            AuthResponse response = authService.register(request);

            assertThat(response)
                    .isNotNull()
                    .extracting(AuthResponse::refreshToken, AuthResponse::accessToken, AuthResponse::tokenType)
                    .containsExactly("random.refresh.token", "random.access.token", "Bearer");
            assertThat(response.userInfo()).isNotNull();
            assertThat(response.userInfo().email()).isEqualTo("test@mail.com");

            ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo("encodedPassword");

            ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
            verify(tokenRepository).save(tokenCaptor.capture());
            RefreshTokenEntity savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(savedUserId);
            assertThat(savedToken.getToken()).isEqualTo("random.refresh.token");
            assertThat(savedToken.isRevoked()).isFalse();
            assertThat(savedToken.getExpiresAt()).isAfterOrEqualTo(ZonedDateTime.now());
        }

        @Test
        void shouldThrowException_WhenEmailAlreadyExists() {
            given(userRepository.existsByEmail(request.email())).willReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(EmailAlreadyExistsException.class)
                    .hasMessageContaining("already exists");

            verify(userRepository, never()).save(any(UserEntity.class));
            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }
    }

    @Nested
    class Login {
        private final LoginRequest request = new LoginRequest(
                "test@mail.com",
                "1234"
        );

        @Test
        void shouldLoginUserSuccessfully_WhenEmailAndPasswordCorrect() {
            UUID userId = UUID.randomUUID();
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setPassword("encodedPassword");
            user.setStatus(UserStatus.ACTIVE);
            user.setEmail("test@mail.com");
            UserProfile profile = new UserProfile(
                    userId,
                    "test@mail.com",
                    "Taylor",
                    "Durden",
                    UserRole.ROLE_USER,
                    UserStatus.ACTIVE
            );

            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(eq(request.password()), anyString())).willReturn(true);
            given(jwtService.generateRefreshToken(user)).willReturn("random.refresh.token");
            given(jwtService.generateAccessToken(user)).willReturn("random.access.token");
            given(jwtService.getRefreshTokenTtl()).willReturn(Duration.ofDays(14));
            given(jwtService.getAccessTokenTtl()).willReturn(Duration.ofMinutes(15));
            given(userMapper.toProfile(any(UserEntity.class))).willReturn(profile);

            AuthResponse response = authService.login(request);

            assertThat(response)
                    .isNotNull()
                    .extracting(AuthResponse::refreshToken, AuthResponse::accessToken, AuthResponse::tokenType)
                    .containsExactly("random.refresh.token", "random.access.token", "Bearer");
            assertThat(response.userInfo()).isNotNull();
            assertThat(response.userInfo().email()).isEqualTo("test@mail.com");

            ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
            verify(tokenRepository).save(tokenCaptor.capture());
            RefreshTokenEntity savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(userId);
            assertThat(savedToken.getToken()).isEqualTo("random.refresh.token");
            assertThat(savedToken.isRevoked()).isFalse();
            assertThat(savedToken.getExpiresAt()).isAfterOrEqualTo(ZonedDateTime.now());
        }

        @Test
        void shouldThrowException_WhenUserDoesNotExistsByEmail() {
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialException.class)
                    .hasMessageContaining("Wrong email or password");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }

        @Test
        void shouldThrowException_WhenUserIsNotActive() {
            UserEntity notActiveUser = new UserEntity();
            notActiveUser.setStatus(UserStatus.BLOCKED);

            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(notActiveUser));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UserBlockedException.class)
                    .hasMessageContaining("Your account has been blocked, please contact support");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }

        @Test
        void shouldThrowException_WhenPasswordDoesNotMatch() {
            UserEntity user = new UserEntity();
            user.setPassword("encodedPassword");

            given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidCredentialException.class)
                    .hasMessageContaining("Wrong email or password");
        }
    }

    @Nested
    class Refresh {
        @Test
        void shouldRefreshSuccessfully() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(ZonedDateTime.now().plusDays(5));

            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setEmail("test@mail.com");
            user.setStatus(UserStatus.ACTIVE);

            UserProfile profile = new UserProfile(
                    userId,
                    "test@mail.com",
                    "Taylor",
                    "Durden",
                    UserRole.ROLE_USER,
                    UserStatus.ACTIVE
            );

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));
            given(userRepository.findById(eq(userId))).willReturn(Optional.of(user));

            given(jwtService.generateRefreshToken(user)).willReturn("new.random.refresh.token");
            given(jwtService.generateAccessToken(user)).willReturn("random.access.token");
            given(jwtService.getRefreshTokenTtl()).willReturn(Duration.ofDays(14));
            given(jwtService.getAccessTokenTtl()).willReturn(Duration.ofMinutes(15));
            given(userMapper.toProfile(any(UserEntity.class))).willReturn(profile);

            AuthResponse response = authService.refreshAccessToken(oldRefreshToken);

            assertThat(response)
                    .isNotNull()
                    .extracting(AuthResponse::refreshToken, AuthResponse::accessToken, AuthResponse::tokenType)
                    .containsExactly("new.random.refresh.token", "random.access.token", "Bearer");
            assertThat(response.userInfo().id()).isEqualTo(userId);

            ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);

            verify(tokenRepository, times(2)).save(tokenCaptor.capture());

            List<RefreshTokenEntity> savedTokens = tokenCaptor.getAllValues();
            assertThat(savedTokens).hasSize(2);

            RefreshTokenEntity firstSave = savedTokens.getFirst();
            assertThat(firstSave.getJti()).isEqualTo(oldJti);
            assertThat(firstSave.isRevoked()).isTrue();

            RefreshTokenEntity secondSave = savedTokens.get(1);
            assertThat(secondSave.getJti()).isNotEqualTo(oldJti);
            assertThat(secondSave.isRevoked()).isFalse();
            assertThat(secondSave.getUserId()).isEqualTo(userId);
            assertThat(secondSave.getExpiresAt()).isAfter(ZonedDateTime.now());
        }

        @Test
        void shouldThrowException_WhenTokenInvalidOrUnknown() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("Invalid or unknown refresh token.");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
            verifyNoMoreInteractions(tokenRepository);
        }

        @Test
        void shouldThrowExceptionAndRevokeAllUserTokens_WhenRefreshTokenFromRequestIsAlreadyRevoked() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(true);
            storedToken.setExpiresAt(ZonedDateTime.now().plusDays(5));

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("Token has been revoked, please log in again.");

            verify(tokenRepository, times(1)).revokeAllByUserId(eq(userId));
            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }

        @Test
        void shouldThrowException_WhenRefreshTokenFromRequestExpired() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(ZonedDateTime.now().minusDays(1));

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(TokenExpiredException.class)
                    .hasMessageContaining("Refresh token has expired. Please log in again.");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
            verifyNoMoreInteractions(tokenRepository);
        }

        @Test
        void shouldThrowException_WhenUserIdParsingFailed() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(ZonedDateTime.now().plusDays(5));

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString().concat("invalidUUID"));
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(InvalidTokenException.class)
                    .hasMessageContaining("Invalid token format.");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
            verifyNoMoreInteractions(tokenRepository);
        }

        @Test
        void shouldThrowException_WhenUserDoesNotFoundByUserIdFromToken() {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(ZonedDateTime.now().plusDays(5));

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));
            given(userRepository.findById(eq(userId))).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User associated with this token no longer exists.");

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
            verifyNoMoreInteractions(tokenRepository);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        void shouldThrowExceptionAndRevokeAllUserTokens_WhenUserIsBlockedOrSuspended(UserStatus status) {
            String oldRefreshToken = "old.refresh.token";
            String oldJti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(oldJti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);
            storedToken.setExpiresAt(ZonedDateTime.now().plusDays(5));

            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setEmail("test@mail.com");
            user.setStatus(status);

            given(jwtService.extractClaims(eq(oldRefreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(oldJti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(oldJti))).willReturn(Optional.of(storedToken));
            given(userRepository.findById(eq(userId))).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshAccessToken(oldRefreshToken))
                    .isInstanceOf(UserBlockedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verify(tokenRepository, times(1)).revokeAllByUserId(eq(userId));
            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
            verifyNoMoreInteractions(tokenRepository);
        }
    }

    @Nested
    class Logout {
        @Test
        void shouldLogoutSuccessfully() {
            String refreshToken = "old.refresh.token";
            String accessToken = "old.access.token";
            String jti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            RefreshTokenEntity storedToken = new RefreshTokenEntity();
            storedToken.setJti(jti);
            storedToken.setUserId(userId);
            storedToken.setRevoked(false);

            given(jwtService.extractClaims(eq(refreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(jti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(jti))).willReturn(Optional.of(storedToken));

            authService.logout(accessToken, refreshToken);

            assertThat(storedToken.isRevoked()).isTrue();
            verify(tokenRepository).save(eq(storedToken));
            verify(tokenRepository, never()).revokeAllByUserId(any());
        }

        @Test
        void shouldDoNothing_WhenRefreshTokenIsNull() {
            authService.logout("some.access.token", null);

            verifyNoInteractions(jwtService, tokenRepository);
        }

        @Test
        void shouldDoNothing_WhenRefreshTokenIsBlank() {
            authService.logout("some.access.token", "   ");

            verifyNoInteractions(jwtService, tokenRepository);
        }

        @Test
        void shouldHandleGracefully_WhenTokenNotFoundInDb() {
            String refreshToken = "old.refresh.token";
            String accessToken = "old.access.token";
            String jti = UUID.randomUUID().toString();
            UUID userId = UUID.randomUUID();

            given(jwtService.extractClaims(eq(refreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willReturn(jti)
                    .willReturn(userId.toString());
            given(tokenRepository.findByJti(eq(jti))).willReturn(Optional.empty());

            authService.logout(accessToken, refreshToken);

            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }

        @Test
        void shouldHandleGracefully_WhenExceptionOccurredDuringProcessing() {
            String refreshToken = "old.refresh.token";
            String accessToken = "old.access.token";

            given(jwtService.extractClaims(eq(refreshToken), eq(TokenType.REFRESH_TOKEN), any()))
                    .willThrow(new IllegalArgumentException("Invalid token format"));

            assertDoesNotThrow(() -> authService.logout(accessToken, refreshToken));

            verify(tokenRepository, never()).findByJti(anyString());
            verify(tokenRepository, never()).save(any(RefreshTokenEntity.class));
        }
    }
}
