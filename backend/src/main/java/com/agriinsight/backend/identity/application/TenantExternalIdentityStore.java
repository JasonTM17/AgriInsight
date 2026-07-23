package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.domain.ExternalIdentity;
import java.util.Optional;
import java.util.UUID;

public interface TenantExternalIdentityStore {

    ExternalIdentityPage findAll(
            ScopeContext scope,
            UUID profileId,
            ExternalIdentityQuery query);

    Optional<ExternalIdentityReference> findById(
            ScopeContext scope,
            UUID profileId,
            UUID identityId);

    Optional<ExternalIdentityReference> link(
            ScopeContext scope,
            ExternalIdentity identity);

    Optional<Long> unlink(
            ScopeContext scope,
            UUID profileId,
            UUID identityId);
}
