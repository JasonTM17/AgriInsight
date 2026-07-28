package com.agriinsight.backend.realtime.application;

/** Signals immutable receipt metadata that conflicts with an earlier Kafka record. */
public class RealtimeEventConflictException extends RuntimeException {

    public RealtimeEventConflictException(String message) {
        super(message);
    }

    public RealtimeEventConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
