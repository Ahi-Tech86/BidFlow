package com.ahicode.bidflow.bidding.dtos;

import java.math.BigDecimal;
import java.util.UUID;

public record BidPlacedEvent(
        UUID lotId,
        BigDecimal newMaxBid,
        UUID newBidderId
) {
}
