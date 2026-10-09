package com.ahicode.bidflow.auth.mappers;

import com.ahicode.bidflow.auth.dtos.RegisterRequest;
import com.ahicode.bidflow.auth.dtos.UserProfile;
import com.ahicode.bidflow.auth.entities.UserEntity;
import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Nested
    class ToEntity {
        @Test
        void shouldMapOnlyAllowedFields_FromRegisterRequest() {
            RegisterRequest request = new RegisterRequest(
                    "test@mail.com",
                    "Taylor",
                    "Durden",
                    "1234"
            );

            UserEntity entity = mapper.toEntity(request);

            assertThat(entity)
                    .isNotNull()
                    .extracting(UserEntity::getEmail, UserEntity::getFirstName, UserEntity::getLastName)
                    .containsExactly("test@mail.com", "Taylor", "Durden");

            assertThat(entity)
                    .extracting(
                            UserEntity::getId, UserEntity::getRole, UserEntity::getStatus,
                            UserEntity::getPassword, UserEntity::getCreatedAt, UserEntity::getUpdatedAt
                    )
                    .containsExactly(null, UserRole.ROLE_USER, UserStatus.ACTIVE, null, null, null);
        }
    }

    @Nested
    class ToProfile {
        @Test
        void shouldMapOnlyAllowedFields_FromUserEntity() {
            UUID id = UUID.randomUUID();
            UserEntity entity = new UserEntity(
                    id,
                    "test@mail.com",
                    "Taylor",
                    "Durden",
                    UserStatus.ACTIVE,
                    UserRole.ROLE_USER,
                    ZonedDateTime.now(),
                    ZonedDateTime.now(),
                    "hashed_password"
            );

            UserProfile profile = mapper.toProfile(entity);

            assertThat(profile)
                    .isNotNull()
                    .extracting(
                            UserProfile::id, UserProfile::email, UserProfile::firstName,
                            UserProfile::lastName, UserProfile::role, UserProfile::status
                    )
                    .containsExactly(
                            id, "test@mail.com", "Taylor", "Durden", UserRole.ROLE_USER, UserStatus.ACTIVE
                    );
        }
    }
}
