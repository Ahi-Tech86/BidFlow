package com.ahicode.bidflow.bidding.dtos;

import com.ahicode.bidflow.bidding.enums.LotStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Schema(description = "Response payload for lot list")
public record LotListItemResponse(
        @Schema(
                description = "Unique lot identifier (UUID)",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID lotId,

        @Schema(
                description = "Unique user identifier (UUID) of the lot owner",
                example = "123e4567-e89b-12d3-a456-426614174000"
        )
        UUID ownerId,

        @Schema(description = "A lot name", example = "Ferrari F40")
        String name,

        @Schema(description = "Start price of a lot", example = "100000.00")
        BigDecimal startPrice,

        @Schema(
                description = "Current status of the lot",
                example = "ACTIVE",
                allowableValues = {"ACTIVE", "FINISHED", "CANCELLED"}
        )
        LotStatus status,

        @Schema(
                description = "Date and time when the lot auction finishes (ISO-8601)",
                example = "2025-12-31T23:59:59+03:00[Europe/Moscow]"
        )
        ZonedDateTime finishedAt,

        @Schema(
                description = "Date and time when the lot was created",
                example = "2025-12-31T23:59:59+03:00[Europe/Moscow]"
        )
        ZonedDateTime createdAt
) {
}
