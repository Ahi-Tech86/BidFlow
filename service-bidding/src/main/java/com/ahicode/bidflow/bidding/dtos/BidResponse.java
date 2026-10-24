package com.ahicode.bidflow.bidding.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Schema(description = "Response payload representing a successfully placed bid")
public record BidResponse(
        @Schema(description = "Unique identifier of the bid", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "Identifier of the lot this bid belongs to", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID lotId,

        @Schema(description = "Identifier of the user who placed the bid", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID userId,

        @Schema(description = "The accepted bid amount", example = "500.00")
        BigDecimal bidValue,

        @Schema(description = "Timestamp of when the bid was created (ISO 8601 format)", example = "2025-10-26T14:30:00Z")
        ZonedDateTime createdAt
) {
}
