package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.PermissionEvaluator;
import com.agriinsight.backend.authorization.application.ScopeResolver;
import com.agriinsight.backend.authorization.application.TenantAuditQuery;
import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAuditReadStore;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAuditPublisher;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAdministratorGuard;
import com.agriinsight.backend.authorization.infrastructure.PostgresTenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.identity.application.ProvisionedTenantUser;
import com.agriinsight.backend.identity.application.ExternalIdentityQuery;
import com.agriinsight.backend.identity.application.TenantUserCommands;
import com.agriinsight.backend.identity.application.TenantUserQuery;
import com.agriinsight.backend.identity.application.TenantUserService;
import com.agriinsight.backend.identity.infrastructure.PostgresTenantExternalIdentityStore;
import com.agriinsight.backend.identity.infrastructure.PostgresTenantUserStore;
import com.agriinsight.backend.persistence.support.SqlTestResources;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.application.VersionConflictException;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TenantAdministrationIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String ISSUER = "https://identity.example.test/issuer";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareTenantFixtures() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, SqlTestResources.projectFile("backend/src/test/resources/sql/rls-fixtures.sql"));
        }
    }

    @Test
    void tenantUserLifecycleIsVersionedAuditedAndBoundToOneTenant() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            JdbcTemplate jdbcTemplate = harness.jdbcTemplate();
            TenantUserService service = service(jdbcTemplate, harness.contextState());
            authenticateAdmin();
            try {
                var audit = new TenantAuditMetadata(
                        Optional.of("ACCESS_APPROVED"),
                        Optional.of("tenant-user-flow-01"));
                ProvisionedTenantUser provisioned = harness.withinTenant(() -> service.create(
                        new TenantUserCommands.Create(
                                "Lifecycle User",
                                Optional.of("lifecycle@example.test"),
                                ISSUER,
                                "subject-lifecycle-primary",
                                audit)));

                assertThat(harness.withinTenant(() -> service.list(new TenantUserQuery(
                        10,
                        0,
                        Optional.of(true),
                        Optional.of("Lifecycle"))).items()))
                        .extracting(profile -> profile.id())
                        .containsExactly(provisioned.profile().id());
                assertThat(harness.withinTenant(() -> service.get(provisioned.profile().id())))
                        .isEqualTo(provisioned.profile());

                var deactivated = harness.withinTenant(() -> service.deactivate(
                        provisioned.profile().id(),
                        new TenantUserCommands.Lifecycle(0, audit)));
                assertThat(deactivated.active()).isFalse();
                assertThat(deactivated.version()).isEqualTo(1);
                assertThatThrownBy(() -> harness.withinTenant(() -> service.reactivate(
                        provisioned.profile().id(),
                        new TenantUserCommands.Lifecycle(0, audit))))
                        .isInstanceOf(VersionConflictException.class);
                var reactivated = harness.withinTenant(() -> service.reactivate(
                        provisioned.profile().id(),
                        new TenantUserCommands.Lifecycle(1, audit)));
                assertThat(reactivated.active()).isTrue();
                assertThat(reactivated.version()).isEqualTo(2);

                var secondary = harness.withinTenant(() -> service.linkIdentity(
                        provisioned.profile().id(),
                        new TenantUserCommands.LinkIdentity(
                                ISSUER,
                                "subject-lifecycle-secondary",
                                audit)));
                assertThat(harness.withinTenant(() -> service.unlinkIdentity(
                        provisioned.profile().id(), secondary.id(), audit))).isEqualTo(1);
                var unlinked = harness.withinTenant(() -> service.getIdentity(
                        provisioned.profile().id(), secondary.id()));
                assertThat(unlinked.active()).isFalse();
                assertThat(unlinked.version()).isEqualTo(1);

                ScopeContext scope = ScopeContext.tenant(new TestPrincipal(ADMIN_A, TENANT_A));
                var identityStore = new PostgresTenantExternalIdentityStore(jdbcTemplate);
                var identityPage = harness.withinTenant(() -> identityStore.findAll(
                        scope,
                        provisioned.profile().id(),
                        new ExternalIdentityQuery(1, 0, Optional.empty())));
                assertThat(identityPage.items()).hasSize(1);
                assertThat(identityPage.hasMore()).isTrue();
                assertThat(harness.withinTenant(() -> identityStore.findAll(
                        scope,
                        provisioned.profile().id(),
                        new ExternalIdentityQuery(10, 0, Optional.of(true))).items()))
                        .hasSize(1)
                        .allMatch(item -> item.active() && item.issuer().equals(ISSUER));

                var auditStore = new PostgresTenantAuditReadStore(jdbcTemplate);
                var auditPage = harness.withinTenant(() -> auditStore.findAll(
                        scope,
                        new TenantAuditQuery(
                                1,
                                0,
                                Optional.of(ADMIN_A),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty())));
                assertThat(auditPage.items()).hasSize(1);
                assertThat(auditPage.hasMore()).isTrue();
                assertThat(auditPage.items()).allMatch(item ->
                        item.actorProfileId().filter(ADMIN_A::equals).isPresent());

                assertAuditTrail();
                assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM agriinsight_security.resolve_identity_bootstrap(?, ?)
                        """, Long.class, ISSUER, "subject-lifecycle-primary")).isEqualTo(1L);
                assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM agriinsight_security.resolve_identity_bootstrap(?, ?)
                        """, Long.class, ISSUER, "subject-lifecycle-secondary")).isZero();
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT agriinsight_security.app_current_tenant_id()",
                        UUID.class)).isNull();
                assertThat(harness.contextState().currentTenantId()).isEmpty();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void authorizationDenialAuditCommitsWhenTheRejectedBusinessTransactionRollsBack() throws Throwable {
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            JdbcTemplate jdbcTemplate = harness.jdbcTemplate();
            var recorder = new PostgresTenantAuthorizationDeniedRecorder(
                    new TenantContextBinder(jdbcTemplate),
                    new PostgresTenantAuditPublisher(jdbcTemplate),
                    harness.transactionManager());
            PermissionEvaluator evaluator = new PermissionEvaluator(
                    new ScopeResolver(List.of()),
                    harness.contextState());
            authenticateAdmin();
            try {
                assertThatThrownBy(() -> harness.withinTenant(recorder, () -> {
                    evaluator.requireTenant(Permission.COST_READ);
                    return null;
                })).isInstanceOf(AccessDeniedException.class);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                    FROM tenant_audit_events
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND actor_profile_id = '20000000-0000-0000-0000-000000000001'
                      AND action = 'AUTHORIZATION_DENIED'
                      AND target_reference = 'permission=COST_READ;scope=TENANT'
                      AND outcome = 'DENIED'
                    """)).isEqualTo(1);
        }
    }

    private void assertAuditTrail() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM tenant_audit_events
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND actor_profile_id = '20000000-0000-0000-0000-000000000001'
                      AND action <> 'AUTHORIZATION_DENIED'
                    """)).isEqualTo(6);
            assertThat(count(operator, """
                    SELECT count(*) FROM tenant_audit_events
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000001'
                      AND target_reference IN ('subject-lifecycle-primary', 'subject-lifecycle-secondary')
                    """)).isZero();
        }
    }

    private TenantUserService service(JdbcTemplate jdbcTemplate, TenantContextState contextState) {
        PermissionEvaluator permissionEvaluator = new PermissionEvaluator(
                new ScopeResolver(List.of()),
                contextState);
        return new TenantUserService(
                permissionEvaluator,
                new PostgresTenantUserStore(jdbcTemplate),
                new PostgresTenantExternalIdentityStore(jdbcTemplate),
                () -> ISSUER,
                new PostgresTenantAdministratorGuard(jdbcTemplate),
                new PostgresTenantAuditPublisher(jdbcTemplate));
    }

    private void authenticateAdmin() {
        TenantPrincipal principal = new TestPrincipal(ADMIN_A, TENANT_A);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(Permission.IDENTITY_USER_MANAGE.authority()),
                                new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }

}
