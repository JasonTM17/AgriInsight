package com.agriinsight.backend.shared.application;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/** Carries redacted tenant-denial metadata to the outer audit and response boundaries. */
public final class TenantAuthorizationDeniedException extends AccessDeniedException {

    private final TenantAuthorizationDeniedRecorder.Decision decision;
    private boolean auditRecorded;

    public TenantAuthorizationDeniedException(TenantAuthorizationDeniedRecorder.Decision decision) {
        super("Access is denied");
        this.decision = Objects.requireNonNull(decision, "decision is required");
    }

    public TenantAuthorizationDeniedRecorder.Decision decision() {
        return decision;
    }

    public boolean auditRecorded() {
        return auditRecorded;
    }

    public void markAuditRecorded() {
        auditRecorded = true;
    }
}
