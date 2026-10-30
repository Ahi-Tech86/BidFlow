package com.ahicode.bidflow.bidding.exceptions;

public class OwnerCannotBidException extends RuntimeException {
    public OwnerCannotBidException() {
        super("Owner cannot bid on their own lot");
    }
}
