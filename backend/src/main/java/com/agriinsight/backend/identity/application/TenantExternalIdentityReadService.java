package com.agriinsight.backend.identity.application;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.TenantScoped;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
@Profile("!test")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class TenantExternalIdentityReadService {

    private final PermissionEvaluator permissions;
    private final TenantUserStore users;
    private final TenantExternalIdentityStore identities;

    public TenantExternalIdentityReadService(
            PermissionEvaluator permissions,
            TenantUserStore users,
            TenantExternalIdentityStore identities) {
        this.permissions = Objects.requireNonNull(permissions, "permissions is required");
        this.users = Objects.requireNonNull(users, "users is required");
        this.identities = Objects.requireNonNull(identities, "identities is required");
    }

    public ExternalIdentityPage list(UUID profileId, ExternalIdentityQuery query) {
        ScopeContext scope = permissions.requireTenant(Permission.IDENTITY_USER_MANAGE);
        UUID target = Objects.requireNonNull(profileId, "profileId is required");
        if (users.findById(scope, target).isEmpty()) {
            throw new ResourceNotFoundException("Tenant user");
        }
        return identities.findAll(
                scope, target, Objects.requireNonNull(query, "query is required"));
    }
}
