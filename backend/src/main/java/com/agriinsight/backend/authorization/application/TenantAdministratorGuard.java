package com.agriinsight.backend.authorization.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.UUID;

public interface TenantAdministratorGuard {

    void assertPathRemains(ScopeContext scope, UUID profileId);
}
