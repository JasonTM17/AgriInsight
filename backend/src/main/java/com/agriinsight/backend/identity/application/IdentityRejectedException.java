package com.agriinsight.backend.identity.application;

import java.util.Objects;

public final class IdentityRejectedException extends RuntimeException {

    private final IdentityRejectionReason reason;

    public IdentityRejectedException(IdentityRejectionReason reason) {
        super("External identity is not active");
        this.reason = Objects.requireNonNull(reason, "reason is required");
    }

    public IdentityRejectionReason reason() {
        return reason;
    }
}
