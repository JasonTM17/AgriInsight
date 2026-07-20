package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;

public interface TenantUserStore {

    TenantUserPage findAll(ScopeContext scope, TenantUserQuery query);

    Optional<TenantUserProfile> findById(ScopeContext scope, UUID profileId);

    TenantUserProfile create(ScopeContext scope, UserProfile profile);

    Optional<TenantUserProfile> updateActive(
            ScopeContext scope,
            UUID profileId,
            long expectedVersion,
            boolean active);

}
