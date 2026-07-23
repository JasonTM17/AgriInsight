package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantExternalIdentityReadServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Mock private PermissionEvaluator permissions;
    @Mock private TenantUserStore users;
    @Mock private TenantExternalIdentityStore identities;

    private ScopeContext scope;
    private ExternalIdentityQuery query;
    private TenantExternalIdentityReadService service;

    @BeforeEach
    void setUp() {
        scope = new ScopeContext(
                TENANT_ID, ACTOR_ID, ScopeContext.Type.TENANT, Optional.empty());
        query = new ExternalIdentityQuery(25, 0, Optional.of(true));
        service = new TenantExternalIdentityReadService(permissions, users, identities);
        when(permissions.requireTenant(Permission.IDENTITY_USER_MANAGE)).thenReturn(scope);
    }

    @Test
    void targetUserMustExistInTheBoundTenant() {
        when(users.findById(scope, PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(PROFILE_ID, query))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(identities, never()).findAll(scope, PROFILE_ID, query);
    }

    @Test
    void existingTargetUsesBoundedRedactedIdentityStore() {
        TenantUserProfile profile = new TenantUserProfile(
                PROFILE_ID, TENANT_ID, "User", Optional.empty(), true, 0);
        ExternalIdentityPage page = new ExternalIdentityPage(List.of(), 25, 0, false);
        when(users.findById(scope, PROFILE_ID)).thenReturn(Optional.of(profile));
        when(identities.findAll(scope, PROFILE_ID, query)).thenReturn(page);

        service.list(PROFILE_ID, query);

        verify(identities).findAll(scope, PROFILE_ID, query);
    }
}
