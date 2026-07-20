package com.agriinsight.backend.shared.api;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("The idempotency key was already used for a different command");
    }
}
