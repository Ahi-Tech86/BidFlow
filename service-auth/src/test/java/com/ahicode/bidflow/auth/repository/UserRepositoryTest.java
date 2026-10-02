package com.ahicode.bidflow.auth.repository;

import com.ahicode.bidflow.auth.TestContainersConfiguration;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import com.ahicode.bidflow.auth.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TestContainersConfiguration.class)
public class UserRepositoryTest {

    private final String TEST_EMAIL = "test@mail.com";
    private UserEntity user;

    @Autowired private UserRepository repository;
    @Autowired private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        user = new UserEntity(
                null,
                TEST_EMAIL,
                "Taylor",
                "Durden",
                UserStatus.ACTIVE,
                UserRole.ROLE_USER,
                null,
                null,
                "password"
        );
    }
    
    @Nested
    class FindByEmail {
        @Test
        void shouldReturnOptionalUser() {
            UserEntity savedUser = entityManager.persistAndFlush(user);
            entityManager.clear();

            Optional<UserEntity> found = repository.findByEmail(TEST_EMAIL);

            assertThat(found)
                    .isPresent()
                    .get()
                    .extracting(UserEntity::getEmail, UserEntity::getId)
                    .containsExactly(TEST_EMAIL, savedUser.getId());
        }

        @Test
        void shouldReturnOptionalEmpty_WhenUserDoesNotExistsByEmail() {
            Optional<UserEntity> found = repository.findByEmail("non-existent@mail.com");

            assertThat(found).isEmpty();
        }

        @Test
        void shouldReturnOptionalEmpty_WhenUserEmailIsNull() {
            Optional<UserEntity> found = repository.findByEmail(null);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    class ExistsByEmail {
        @Test
        void shouldReturnTrue_WhenUserExistsByEmail() {
            entityManager.persistAndFlush(user);
            entityManager.clear();

            boolean expected = repository.existsByEmail(TEST_EMAIL);

            assertTrue(expected);
        }

        @Test
        void shouldReturnFalse_WhenUserDoesNotExistsByEmail() {
            boolean expected = repository.existsByEmail("non-existent@mail.com");

            assertFalse(expected);
        }
    }
}
