package com.ahicode.bidflow.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Response payload for authenticated user")
public record AuthResponse(
        @Schema(
                description = "JWT Refresh Token used to obtain new Access Token",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abc123...",
                required = true
        )
        String refreshToken,

        @Schema(
                description = "JWT Access Token used for authorized requests",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.xyz789",
                required = true
        )
        String accessToken,

        @Schema(
                description = "Type of token, typically 'Bearer'",
                example = "Bearer",
                required = true
        )
        String tokenType,

        @Schema(
                description = "Lifetime of Access Token in seconds",
                example = "450",
                required = true
        )
        long expiresIn,

        @Schema(
                description = "Authenticated user's profile information",
                required = true
        )
        UserProfile userInfo
) {
}
