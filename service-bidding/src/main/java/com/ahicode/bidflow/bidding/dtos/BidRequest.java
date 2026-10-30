package com.ahicode.bidflow.bidding.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Request payload for placing a bit on lot")
public record BidRequest(
        @Schema(
                description = "The proposed bid amount. Must be strictly positive. " +
                        "Must be greater than or equals to the starting price and " +
                        "the current highest bid plus the minimum bid step",
                example = "5000.00",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Positive
        BigDecimal bidValue
) {
}
