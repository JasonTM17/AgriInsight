package com.agriinsight.backend.shared.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public final class TenantContextState {

    private final Object resourceKey = new Object();

    public void bind(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TenantContextRequiredException("Tenant context requires an active transaction");
        }
        if (TransactionSynchronizationManager.hasResource(resourceKey)) {
            throw new TenantContextRequiredException("Tenant context is already bound");
        }
        TransactionSynchronizationManager.bindResource(resourceKey, tenantId);
    }

    public void requireBound(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !tenantId.equals(TransactionSynchronizationManager.getResource(resourceKey))) {
            throw new TenantContextRequiredException("Matching tenant context is required");
        }
    }

    public Optional<UUID> currentTenantId() {
        Object tenantId = TransactionSynchronizationManager.getResource(resourceKey);
        return tenantId instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
    }

    public void unbind() {
        if (!TransactionSynchronizationManager.hasResource(resourceKey)) {
            throw new TenantContextRequiredException("Tenant context is not bound");
        }
        TransactionSynchronizationManager.unbindResource(resourceKey);
    }
}
