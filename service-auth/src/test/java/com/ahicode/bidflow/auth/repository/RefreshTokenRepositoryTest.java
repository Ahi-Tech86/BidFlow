package com.ahicode.bidflow.auth.repository;

import com.ahicode.bidflow.auth.TestContainersConfiguration;
import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@Import(TestContainersConfiguration.class)
public class RefreshTokenRepositoryTest {

    private RefreshTokenEntity token;
    private UserEntity testUser;

    @Autowired
    private RefreshTokenRepository repository;
    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity(
                null,
                "test@mail.com",
                "Taylor",
                "Durden",
                UserStatus.ACTIVE,
                UserRole.ROLE_USER,
                null,
                null,
                "password"
        );
        testUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        token = new RefreshTokenEntity(
                null,
                UUID.randomUUID().toString(),
                testUser.getId(),
                "random.refresh.token",
                ZonedDateTime.now().plusDays(15),
                false,
                ZonedDateTime.now()
        );
    }

    @Nested
    class FindAllByUserIdAndRevokedFalse {
        @Test
        void shouldReturnTokensList_WhenTokensHasBeenFound() {
            entityManager.persistAndFlush(token);
            entityManager.clear();

            List<RefreshTokenEntity> foundTokensList = repository.findAllByUserIdAndRevokedFalse(testUser.getId());

            assertThat(foundTokensList)
                    .isNotEmpty()
                    .hasSize(1)
                    .first()
                    .extracting(RefreshTokenEntity::getToken, RefreshTokenEntity::isRevoked)
                    .containsExactly("random.refresh.token", false);
        }

        @Test
        void shouldReturnEmptyList_WhenTokensDoesNotFound() {
            List<RefreshTokenEntity> foundTokensList = repository.findAllByUserIdAndRevokedFalse(testUser.getId());

            assertThat(foundTokensList)
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyList_WhenUserIdIsNull() {
            List<RefreshTokenEntity> foundTokensList = repository.findAllByUserIdAndRevokedFalse(null);

            assertThat(foundTokensList)
                    .isEmpty();
        }
    }

    @Nested
    class FindAllByUserId {
        @Test
        void shouldReturnAllUserTokens() {
            RefreshTokenEntity token1 = makeRefreshToken(testUser.getId(), "random.refresh.token1");
            RefreshTokenEntity token2 = makeRefreshToken(testUser.getId(), "random.refresh.token2");
            RefreshTokenEntity token3 = makeRefreshToken(testUser.getId(), "random.refresh.token3");
            entityManager.persistAndFlush(token1);
            entityManager.persistAndFlush(token2);
            entityManager.persistAndFlush(token3);
            entityManager.clear();

            List<RefreshTokenEntity> found = repository.findAllByUserId(testUser.getId());

            assertThat(found)
                    .isNotEmpty()
                    .hasSize(3);

            found.forEach(token -> assertThat(token.getUserId().equals(testUser.getId())));
        }

        @Test
        void shouldReturnAllTokens_ForSpecificUser() {
            UserEntity testUser2 = new UserEntity(
                    null,
                    "test2@mail.com",
                    "Taylor",
                    "Durden",
                    UserStatus.ACTIVE,
                    UserRole.ROLE_USER,
                    null,
                    null,
                    "password"
            );
            entityManager.persistAndFlush(testUser2);
            entityManager.clear();

            RefreshTokenEntity token1 = makeRefreshToken(testUser.getId(), "random.refresh.token1");
            RefreshTokenEntity token2 = makeRefreshToken(testUser2.getId(), "random.refresh.token2");
            RefreshTokenEntity token3 = makeRefreshToken(testUser.getId(), "random.refresh.token3");
            entityManager.persistAndFlush(token1);
            entityManager.persistAndFlush(token2);
            entityManager.persistAndFlush(token3);
            entityManager.clear();

            List<RefreshTokenEntity> found = repository.findAllByUserId(testUser.getId());

            assertThat(found)
                    .isNotEmpty()
                    .hasSize(2);

            found.forEach(token -> assertThat(token.getUserId().equals(testUser.getId())));
        }

        @Test
        void shouldDoNothing_WhenUserDontHaveTokens() {
            assertThatCode(() -> repository.findAllByUserId(testUser.getId()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class FindByJti {
        @Test
        void shouldReturnOptionalToken_WhenTokenFoundByJti() {
            RefreshTokenEntity savedToken = entityManager.persistAndFlush(token);
            entityManager.clear();

            Optional<RefreshTokenEntity> found = repository.findByJti(savedToken.getJti());

            assertThat(found).isPresent();
            assertThat(found.get().getJti()).isEqualTo(savedToken.getJti());
        }

        @Test
        void shouldReturnOptionalEmpty_WhenTokenDoesNotFoundByJti() {
            Optional<RefreshTokenEntity> found = repository.findByJti(UUID.randomUUID().toString());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class RevokeAllByUserId {
        @Test
        void shouldRevokeAllActiveTokens() {
            RefreshTokenEntity token1 = makeRefreshToken(testUser.getId(), "random.refresh.token1");
            RefreshTokenEntity token2 = makeRefreshToken(testUser.getId(), "random.refresh.token2");
            RefreshTokenEntity token3 = makeRefreshToken(testUser.getId(), "random.refresh.token3");
            entityManager.persistAndFlush(token1);
            entityManager.persistAndFlush(token2);
            entityManager.persistAndFlush(token3);
            entityManager.clear();

            repository.revokeAllByUserId(testUser.getId());
            entityManager.clear();

            List<RefreshTokenEntity> userTokens = repository.findAllByUserId(testUser.getId());
            userTokens.forEach(token -> {
                assertThat(token.getUserId()).isEqualTo(testUser.getId());
                assertThat(token.isRevoked());
            });
        }

        @Test
        void shouldRevokeAllActiveTokens_ForSpecificUser() {
            UserEntity testUser2 = new UserEntity(
                    null,
                    "test2@mail.com",
                    "Taylor",
                    "Durden",
                    UserStatus.ACTIVE,
                    UserRole.ROLE_USER,
                    null,
                    null,
                    "password"
            );
            entityManager.persistAndFlush(testUser2);
            entityManager.clear();

            RefreshTokenEntity token1 = makeRefreshToken(testUser.getId(), "random.refresh.token1");
            RefreshTokenEntity token2 = makeRefreshToken(testUser2.getId(), "random.refresh.token2");
            RefreshTokenEntity token3 = makeRefreshToken(testUser.getId(), "random.refresh.token3");
            entityManager.persistAndFlush(token1);
            entityManager.persistAndFlush(token2);
            entityManager.persistAndFlush(token3);
            entityManager.clear();

            repository.revokeAllByUserId(testUser.getId());
            entityManager.clear();

            List<RefreshTokenEntity> firstUserTokens = repository.findAllByUserId(testUser.getId());
            firstUserTokens.forEach(token -> {
                assertThat(token.getUserId()).isEqualTo(testUser.getId());
                assertThat(token.isRevoked());
            });

            List<RefreshTokenEntity> secondUserTokens = repository.findAllByUserId(testUser2.getId());
            secondUserTokens.forEach(token -> {
                assertThat(token.getUserId()).isEqualTo(testUser2.getId());
                assertThat(!token.isRevoked());
            });
        }

        @Test
        void shouldRevokedAllActiveTokens_AndIgnoreAlreadyRevokedTokens() {
            RefreshTokenEntity token1 = makeRefreshToken(testUser.getId(), "random.refresh.token1");
            RefreshTokenEntity token2 = makeRefreshToken(testUser.getId(), "random.refresh.token2");
            token2.setRevoked(true);
            entityManager.persistAndFlush(token1);
            entityManager.persistAndFlush(token2);
            entityManager.clear();

            repository.revokeAllByUserId(testUser.getId());
            entityManager.clear();

            List<RefreshTokenEntity> userTokens = repository.findAllByUserId(testUser.getId());
            userTokens.forEach(token -> {
                if (token.getId().equals(token1.getId())) {
                    assertThat(token1.isRevoked());
                }

                if (token.getId().equals(token2.getId())) {
                    assertThat(token2.isRevoked());
                }
            });
        }

        @Test
        void shouldDoNothing_WhenUserDontHaveTokens() {
            assertThatCode(() -> repository.revokeAllByUserId(testUser.getId()))
                    .doesNotThrowAnyException();
        }
    }

    private RefreshTokenEntity makeRefreshToken(UUID userId, String tokenValue) {
        return new RefreshTokenEntity(
                null,
                UUID.randomUUID().toString(),
                userId,
                tokenValue,
                ZonedDateTime.now().plusDays(15),
                false,
                ZonedDateTime.now()
        );
    }
}
