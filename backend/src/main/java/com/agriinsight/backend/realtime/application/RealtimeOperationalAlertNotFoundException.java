package com.agriinsight.backend.realtime.application;

import com.agriinsight.backend.shared.application.ResourceNotFoundException;

/** Raised when an alert is not visible inside the caller's current tenant scope. */
public class RealtimeOperationalAlertNotFoundException extends ResourceNotFoundException {

    public RealtimeOperationalAlertNotFoundException() {
        super("Operational alert");
    }
}
