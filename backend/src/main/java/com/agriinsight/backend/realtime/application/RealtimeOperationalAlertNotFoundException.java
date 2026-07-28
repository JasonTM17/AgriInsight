package com.agriinsight.backend.realtime.application;

/** Raised when an alert is not visible inside the caller's current tenant scope. */
public class RealtimeOperationalAlertNotFoundException extends RuntimeException {

    public RealtimeOperationalAlertNotFoundException() {
        super("Operational alert was not found");
    }
}
