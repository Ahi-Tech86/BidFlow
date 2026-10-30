package com.ahicode.bidflow.bidding.exceptions;

public class OwnerCannotCancelException extends RuntimeException {
    public OwnerCannotCancelException() {
        super("Only the lot owner can cancel the auction.");
    }
}
