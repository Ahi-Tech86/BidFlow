package com.ahicode.bidflow.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request payload for user login")
public record LoginRequest(
        @NotBlank
        @Email(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        @Schema(description = "User's email", example = "user@bidflow.com")
        String email,

        @NotBlank
        @Size(min = 4, max = 64)
        @Schema(description = "Raw user's password", example = "simple_password")
        String password
) {
}
