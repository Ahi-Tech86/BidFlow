package com.ahicode.bidflow.bidding.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Internal representation of the current bidding state for caching")
public record CurrentBidInfo(
        @Schema(description = "Current max bid value")
        BigDecimal currentMaxBid,

        @Schema(description = "ID of the user who placed the highest bid")
        UUID highestBidderId
) {
}
