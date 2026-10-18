package com.ahicode.bidflow.bidding.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Builder
@Schema(description = "Request payload for create a new lot")
public record CreateLotRequest(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "A lot name", example = "Ferrari F40")
        String name,

        @Size(max = 500)
        @Schema(description = "Detailed information about lot", example = "Rare classic car, excellent condition.")
        String description,

        @Positive
        @Schema(description = "Start price of a lot", example = "100000.00")
        BigDecimal startPrice,

        @Positive
        @Schema(description = "Minimal bid step", example = "100.50")
        BigDecimal minBidStep,

        @Future
        @Schema(
                description = "Date and time when the lot auction finishes (ISO-8601)",
                example = "2025-12-31T23:59:59+03:00[Europe/Moscow]"
        )
        ZonedDateTime finishedAt
) {
}
