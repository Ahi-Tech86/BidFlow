package com.ahicode.bidflow.bidding.exceptions;

import com.ahicode.bidflow.bidding.enums.LotStatus;

import java.util.UUID;

public class LotNotActiveException extends RuntimeException {
    public LotNotActiveException(UUID lotId, LotStatus status) {
        super("Lot " + lotId + " is not active. Current status: " + status);
    }
}
