package com.ahicode.bidflow.bidding.exceptions;

import java.util.UUID;

public class LotNotFoundException extends RuntimeException {
    public LotNotFoundException(UUID lotId) {
        super("Lot with id " + lotId + " not found.");
    }
}
