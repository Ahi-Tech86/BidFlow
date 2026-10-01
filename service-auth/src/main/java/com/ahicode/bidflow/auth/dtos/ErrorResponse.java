package com.ahicode.bidflow.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Standard error response structure returned by the API in case of failures")
public record ErrorResponse(
        @Schema(description = "Timestamp when the error occurred", example = "2026-07-07T14:30:00")
        LocalDateTime timestamp,

        @Schema(description = "HTTP status code", example = "200")
        int status,

        @Schema(description = "Short error type or HTTP reason phrase", example = "Bad Request")
        String error,

        @Schema(description = "Detailed human-readable error message", example = "Validation failed for field 'email'")
        String message,

        @Schema(description = "Request path that triggered the error", example = "/api/v1/admin/licenses/pending")
        String path
) {
}
