package com.agriinsight.backend.shared.security;

import java.security.Principal;
import java.util.UUID;

public interface TenantPrincipal extends Principal {

    UUID profileId();

    UUID tenantId();
}
