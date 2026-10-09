package com.ahicode.bidflow.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for user logout")
public record LogoutRequest(
        @Schema(
                description = "The Refresh Token to be invalidated. Must be a valid JWT format.",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123...",
                required = true
        )
        @NotBlank(message = "Refresh token must not be blank")
        @Pattern(
                regexp = "^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$",
                message = "Invalid refresh token format"
        )
        String refreshToken
) {
}
