package com.agriinsight.backend.shared.persistence;

public final class TenantContextRequiredException extends RuntimeException {

    public TenantContextRequiredException(String message) {
        super(message);
    }
}
