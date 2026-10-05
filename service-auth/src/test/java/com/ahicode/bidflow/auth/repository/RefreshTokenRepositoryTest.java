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

@DataJpaTest
@Import(TestContainersConfiguration.class)
public class RefreshTokenRepositoryTest {

    private RefreshTokenEntity token;
    private UserEntity testUser;

    @Autowired private RefreshTokenRepository repository;
    @Autowired private TestEntityManager entityManager;

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
}
