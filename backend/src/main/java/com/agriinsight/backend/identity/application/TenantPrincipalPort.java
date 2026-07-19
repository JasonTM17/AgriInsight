package com.agriinsight.backend.identity.application;

import java.util.Optional;
import java.util.UUID;

public interface TenantPrincipalPort {

    Optional<TenantPrincipalData> findActiveByProfileAndTenant(UUID profileId, UUID tenantId);
}
