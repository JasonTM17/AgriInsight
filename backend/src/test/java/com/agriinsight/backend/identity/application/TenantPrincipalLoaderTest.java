package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.identity.domain.Permission;
import com.agriinsight.backend.identity.domain.Role;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class TenantPrincipalLoaderTest {

    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = "subject-001";

    private final IdentityBootstrapPort bootstrapPort = mock(IdentityBootstrapPort.class);
    private final TenantPrincipalPort principalPort = mock(TenantPrincipalPort.class);
    private final TenantContextBinder contextBinder = mock(TenantContextBinder.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private TenantPrincipalLoader loader;

    @BeforeEach
    void createLoader() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        loader = new TenantPrincipalLoader(bootstrapPort, principalPort, contextBinder, transactionManager);
    }

    @Test
    void bootstrapsThenLoadsDatabaseRolesInsideATenantTransaction() {
        ExternalIdentityClaims claims = claims();
        IdentityBootstrap bootstrap = new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, true);
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.of(bootstrap));
        when(principalPort.findActiveByProfileAndTenant(PROFILE_ID, TENANT_ID))
                .thenReturn(Optional.of(data()));

        AgriInsightPrincipal principal = loader.load(claims);

        assertThat(principal.displayName()).contains("Database Display");
        assertThat(principal.email()).contains("database@example.test");
        assertThat(principal.assurance()).contains("mfa");
        assertThat(principal.roles()).containsExactly(Role.TENANT_ADMIN);
        assertThat(principal.permissions()).containsExactlyInAnyOrder(
                Permission.IDENTITY_USER_MANAGE,
                Permission.IDENTITY_ROLE_MANAGE);
        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        var order = inOrder(bootstrapPort, transactionManager, contextBinder, principalPort);
        order.verify(bootstrapPort).findByIssuerAndSubject(ISSUER, SUBJECT);
        order.verify(transactionManager).getTransaction(definition.capture());
        order.verify(contextBinder).bind(TENANT_ID);
        order.verify(principalPort).findActiveByProfileAndTenant(PROFILE_ID, TENANT_ID);
        assertThat(definition.getValue().isReadOnly()).isTrue();
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void unknownOrInactiveBootstrapNeverStartsTenantLoading() {
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        assertRejected(IdentityRejectionReason.UNKNOWN_IDENTITY);
        verifyNoInteractions(contextBinder, principalPort);

        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, false, true)));
        assertRejected(IdentityRejectionReason.PROFILE_DISABLED);

        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, false)));
        assertRejected(IdentityRejectionReason.TENANT_DISABLED);
    }

    @Test
    void profileDeactivatedBetweenBootstrapAndScopedLoadFailsClosed() {
        when(bootstrapPort.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new IdentityBootstrap(PROFILE_ID, TENANT_ID, true, true)));
        when(principalPort.findActiveByProfileAndTenant(PROFILE_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertRejected(IdentityRejectionReason.PROFILE_DISABLED);
        verify(transactionManager).rollback(transactionStatus);
    }

    private void assertRejected(IdentityRejectionReason expected) {
        assertThatThrownBy(() -> loader.load(claims()))
                .isInstanceOfSatisfying(IdentityRejectedException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(expected));
    }

    private ExternalIdentityClaims claims() {
        return new ExternalIdentityClaims(
                ISSUER,
                SUBJECT,
                "Untrusted token display",
                "untrusted@example.test",
                "mfa");
    }

    private TenantPrincipalData data() {
        return new TenantPrincipalData(
                PROFILE_ID,
                TENANT_ID,
                "TENANT-A",
                "Database Display",
                Optional.of("database@example.test"),
                Set.of(Role.TENANT_ADMIN),
                Set.of(Permission.IDENTITY_USER_MANAGE, Permission.IDENTITY_ROLE_MANAGE));
    }
}
