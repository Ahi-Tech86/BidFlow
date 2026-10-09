package com.ahicode.bidflow.auth.dtos;

import com.ahicode.bidflow.auth.enums.UserRole;
import com.ahicode.bidflow.auth.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.UUID;

@Builder
@Schema(description = "Public user profile information")
public record UserProfile(

        @Schema(
                description = "Unique user identifier (UUID)",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID id,

        @Schema(
                description = "User's email address",
                example = "user@bidflow.com"
        )
        String email,

        @Schema(
                description = "User's first name",
                example = "Taylor"
        )
        String firstName,

        @Schema(
                description = "User's last name",
                example = "Durden"
        )
        String lastName,

        @Schema(
                description = "User's role in the system",
                example = "USER_ROLE",
                allowableValues = {"USER_ROLE", "MODERATOR_ROLE", "ADMIN_ROLE"}
        )
        UserRole role,

        @Schema(
                description = "Current account status",
                example = "VERIFIED",
                allowableValues = {"ACTIVE", "SUSPENDED", "BLOCKED"}
        )
        UserStatus status
) {
}
