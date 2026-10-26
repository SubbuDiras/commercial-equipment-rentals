package com.marketplace.exception;

public class InvalidStateTransitionException extends RuntimeException {
    private final String currentStatus;
    private final String attemptedStatus;

    public InvalidStateTransitionException(String currentStatus, String attemptedStatus) {
        super(String.format("Cannot transition booking from %s to %s", currentStatus, attemptedStatus));
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getAttemptedStatus() {
        return attemptedStatus;
    }
}
