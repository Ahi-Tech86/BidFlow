package com.ahicode.bidflow.bidding.exceptions;

public class UserNotAllowedException extends RuntimeException {
    public UserNotAllowedException() {
        super("Your account has been suspended or blocked, please contact support");
    }
}
