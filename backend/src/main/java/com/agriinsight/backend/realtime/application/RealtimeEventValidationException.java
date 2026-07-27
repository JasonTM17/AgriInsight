package com.agriinsight.backend.realtime.application;

/** Signals that a Kafka record cannot satisfy the immutable operational event v1 contract. */
public class RealtimeEventValidationException extends RuntimeException {

    public RealtimeEventValidationException(String message) {
        super(message);
    }

    public RealtimeEventValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
