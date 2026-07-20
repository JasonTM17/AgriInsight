package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.ScopeResolver;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentCommands;
import com.agriinsight.backend.authorization.application.TenantRoleAssignmentService;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAuditPublisher;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAdministratorGuard;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantRoleAssignmentStore;
import com.agriinsight.backend.persistence.support.SqlTestResources;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.application.ResourceNotFoundException;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TenantRoleAssignmentIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B_ADMIN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID BACKUP_ADMIN = UUID.fromString("20000000-0000-0000-0000-000000000004");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareTenantFixtures() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, SqlTestResources.projectFile("backend/src/test/resources/sql/rls-fixtures.sql"));
            execute(operator, """
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (
                        '20000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000001',
                        'Backup Admin A'
                    );
                    INSERT INTO external_identities (
                        id, tenant_id, user_profile_id, issuer, subject
                    ) VALUES (
                        '21000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000004',
                        'https://identity.example.test/issuer',
                        'subject-a-backup'
                    );
                    """);
        }
    }

    @Test
    void roleLifecycleIsVersionedAuditedTenantBoundAndPreservesAnAdmin() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            TenantRoleAssignmentService service = service(harness);
            authenticateAdmin();
            try {
                TenantAuditMetadata audit = new TenantAuditMetadata(
                        Optional.of("ACCESS_APPROVED"),
                        Optional.of("tenant-role-flow-01"));
                var granted = harness.withinTenant(() -> service.grant(
                        BACKUP_ADMIN,
                        new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 0, audit)));
                var revoked = harness.withinTenant(() -> service.revoke(
                        BACKUP_ADMIN,
                        Role.DATA_ANALYST,
                        new TenantRoleAssignmentCommands.Revoke(0, audit)));
                assertThat(revoked.id()).isEqualTo(granted.id());
                assertThat(revoked.version()).isEqualTo(1);

                Throwable staleGrant = catchThrowable(() -> harness.withinTenant(() -> service.grant(
                        BACKUP_ADMIN,
                        new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 0, audit))));
                assertThat(staleGrant).isInstanceOf(VersionConflictException.class);
                var regranted = harness.withinTenant(() -> service.grant(
                        BACKUP_ADMIN,
                        new TenantRoleAssignmentCommands.Grant(Role.DATA_ANALYST, 1, audit)));
                assertThat(regranted.id()).isEqualTo(granted.id());
                assertThat(regranted.version()).isEqualTo(2);

                Throwable crossTenant = catchThrowable(() -> harness.withinTenant(() -> service.grant(
                        TENANT_B_ADMIN,
                        new TenantRoleAssignmentCommands.Grant(Role.EXECUTIVE, 0, audit))));
                assertThat(crossTenant).isInstanceOf(ResourceNotFoundException.class);

                Throwable finalAdmin = catchThrowable(() -> harness.withinTenant(() -> service.revoke(
                        ADMIN_A,
                        Role.TENANT_ADMIN,
                        new TenantRoleAssignmentCommands.Revoke(0, audit))));
                assertThat(finalAdmin).isInstanceOf(DataIntegrityViolationException.class);
                assertThat(finalAdmin).rootCause().isInstanceOfSatisfying(PSQLException.class, error ->
                        assertThat(error.getServerErrorMessage().getConstraint())
                                .isEqualTo("tenant_last_active_admin_path"));

                harness.withinTenant(() -> service.grant(
                        BACKUP_ADMIN,
                        new TenantRoleAssignmentCommands.Grant(Role.TENANT_ADMIN, 0, audit)));
                var originalRevoked = harness.withinTenant(() -> service.revoke(
                        ADMIN_A,
                        Role.TENANT_ADMIN,
                        new TenantRoleAssignmentCommands.Revoke(0, audit)));
                assertThat(originalRevoked.active()).isFalse();
                assertThat(originalRevoked.version()).isEqualTo(1);

                assertDatabaseState();
                assertThat(harness.contextState().currentTenantId()).isEmpty();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private TenantRoleAssignmentService service(TenantTransactionTestHarness harness) {
        PermissionEvaluator permissionEvaluator = new PermissionEvaluator(
                new ScopeResolver(List.of()),
                harness.contextState());
        return new TenantRoleAssignmentService(
                permissionEvaluator,
                new PostgresTenantRoleAssignmentStore(harness.jdbcTemplate()),
                new PostgresTenantAdministratorGuard(harness.jdbcTemplate()),
                new PostgresTenantAuditPublisher(harness.jdbcTemplate()));
    }

    private void assertDatabaseState() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM user_roles
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND user_profile_id = '20000000-0000-0000-0000-000000000004'
                      AND role_code = 'TENANT_ADMIN'
                      AND revoked_at IS NULL
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM user_roles
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND user_profile_id = '20000000-0000-0000-0000-000000000001'
                      AND role_code = 'TENANT_ADMIN'
                      AND revoked_at IS NOT NULL
                      AND version = 1
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM tenant_audit_events
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND actor_profile_id = '20000000-0000-0000-0000-000000000001'
                      AND action IN ('ROLE_GRANTED', 'ROLE_REVOKED')
                    """)).isEqualTo(5);
        }
    }

    private void authenticateAdmin() {
        TenantPrincipal principal = new TestPrincipal(ADMIN_A, TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(Permission.IDENTITY_ROLE_MANAGE.authority()),
                                new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
