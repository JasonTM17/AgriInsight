package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.TenantAdministratorGuard;
import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.domain.ExternalIdentity;
import com.agriinsight.backend.identity.domain.UserProfile;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.ResourceStateConflictException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TenantUserServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = "provider-subject-002";
    private static final ScopeContext SCOPE = new ScopeContext(
            TENANT_ID,
            ACTOR_ID,
            ScopeContext.Type.TENANT,
            Optional.empty());
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(
                    Optional.of("ACCESS_APPROVED"),
                    Optional.of("request-01"));

    private final PermissionEvaluator permissionEvaluator = mock(PermissionEvaluator.class);
    private final TenantUserStore store = mock(TenantUserStore.class);
    private final TenantExternalIdentityStore externalIdentities = mock(TenantExternalIdentityStore.class);
    private final TenantAdministratorGuard administratorGuard = mock(TenantAdministratorGuard.class);
    private final TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
    private TenantUserService service;

    @BeforeEach
    void createService() {
        when(permissionEvaluator.requireTenant(Permission.IDENTITY_USER_MANAGE)).thenReturn(SCOPE);
        service = new TenantUserService(
                permissionEvaluator,
                store,
                externalIdentities,
                () -> ISSUER,
                administratorGuard,
                auditPublisher);
    }

    @Test
    void createsProfileAndExactIdentityBeforePublishingRedactedAudit() {
        when(store.create(any(), any(UserProfile.class))).thenAnswer(invocation -> {
            UserProfile profile = invocation.getArgument(1);
            return profile(profile.getId(), true, 0);
        });
        when(externalIdentities.link(any(), any(ExternalIdentity.class))).thenAnswer(invocation -> {
            ExternalIdentity identity = invocation.getArgument(1);
            return Optional.of(new ExternalIdentityReference(identity.getId(), identity.getIssuer(), true, 0));
        });
        TenantUserCommands.Create command = new TenantUserCommands.Create(
                " New User ",
                Optional.of(" new.user@example.test "),
                ISSUER,
                SUBJECT,
                AUDIT);

        ProvisionedTenantUser provisioned = service.create(command);

        assertThat(provisioned.profile().displayName()).isEqualTo("New User");
        assertThat(provisioned.profile().email()).contains("new.user@example.test");
        ArgumentCaptor<ExternalIdentity> identity = ArgumentCaptor.forClass(ExternalIdentity.class);
        verify(externalIdentities).link(eq(SCOPE), identity.capture());
        assertThat(identity.getValue().getSubject()).isEqualTo(SUBJECT);
        ArgumentCaptor<TenantAuditEvent> events = ArgumentCaptor.forClass(TenantAuditEvent.class);
        verify(auditPublisher, times(2)).publish(events.capture());
        assertThat(events.getAllValues())
                .extracting(TenantAuditEvent::action)
                .containsExactly(
                        TenantAuditEvent.Action.USER_CREATED,
                        TenantAuditEvent.Action.EXTERNAL_IDENTITY_LINKED);
        assertThat(events.getAllValues()).allSatisfy(event ->
                assertThat(event.toString()).doesNotContain(SUBJECT, "new.user@example.test"));
    }

    @Test
    void rejectsAnIssuerThatDoesNotExactlyMatchConfiguration() {
        TenantUserCommands.Create command = new TenantUserCommands.Create(
                "New User",
                Optional.empty(),
                ISSUER + "/",
                SUBJECT,
                AUDIT);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured identity provider");
        verifyNoInteractions(store, externalIdentities, auditPublisher);
    }

    @Test
    void deactivationGuardsTheAdminPathBeforeVersionedUpdateAndAudit() {
        TenantUserProfile deactivated = profile(PROFILE_ID, false, 4);
        when(store.updateActive(SCOPE, PROFILE_ID, 3, false)).thenReturn(Optional.of(deactivated));

        assertThat(service.deactivate(PROFILE_ID, new TenantUserCommands.Lifecycle(3, AUDIT)))
                .isEqualTo(deactivated);

        var order = inOrder(administratorGuard, store, auditPublisher);
        order.verify(administratorGuard).assertPathRemains(SCOPE, PROFILE_ID);
        order.verify(store).updateActive(SCOPE, PROFILE_ID, 3, false);
        order.verify(auditPublisher).publish(any(TenantAuditEvent.class));
    }

    @Test
    void staleAndDuplicateLifecycleCommandsReturnTypedConflictsWithoutAudit() {
        when(store.updateActive(SCOPE, PROFILE_ID, 1, true)).thenReturn(Optional.empty());
        when(store.findById(SCOPE, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID, false, 2)));
        assertThatThrownBy(() -> service.reactivate(
                PROFILE_ID,
                new TenantUserCommands.Lifecycle(1, AUDIT)))
                .isInstanceOfSatisfying(VersionConflictException.class, exception -> {
                    assertThat(exception.expectedVersion()).isEqualTo(1);
                    assertThat(exception.currentVersion()).isEqualTo(2);
                });

        when(store.updateActive(SCOPE, PROFILE_ID, 2, true)).thenReturn(Optional.empty());
        when(store.findById(SCOPE, PROFILE_ID)).thenReturn(Optional.of(profile(PROFILE_ID, true, 2)));
        assertThatThrownBy(() -> service.reactivate(
                PROFILE_ID,
                new TenantUserCommands.Lifecycle(2, AUDIT)))
                .isInstanceOf(ResourceStateConflictException.class)
                .hasMessageContaining("already active");
        verify(auditPublisher, never()).publish(any());
    }

    @Test
    void missingIdentityCannotProduceASuccessAudit() {
        UUID identityId = UUID.fromString("21000000-0000-0000-0000-000000000002");
        when(externalIdentities.unlink(SCOPE, PROFILE_ID, identityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlinkIdentity(PROFILE_ID, identityId, AUDIT))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Active external identity");
        verify(auditPublisher, never()).publish(any());
    }

    private TenantUserProfile profile(UUID profileId, boolean active, long version) {
        return new TenantUserProfile(
                profileId,
                TENANT_ID,
                "New User",
                Optional.of("new.user@example.test"),
                active,
                version);
    }
}
