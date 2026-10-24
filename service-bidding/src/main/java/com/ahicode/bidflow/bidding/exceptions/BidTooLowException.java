package com.ahicode.bidflow.bidding.exceptions;

public class BidTooLowException extends RuntimeException {
    public BidTooLowException(String message) {
        super(message);
    }
}
