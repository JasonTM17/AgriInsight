package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.RUNTIME;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.RUNTIME_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.jdbcUrl;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.infrastructure.TenantTransactionAspect;
import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.identity.application.ExternalIdentityClaims;
import com.agriinsight.backend.identity.application.TenantPrincipalLoader;
import com.agriinsight.backend.identity.infrastructure.PostgresIdentityBootstrapRepository;
import com.agriinsight.backend.identity.infrastructure.PostgresTenantPrincipalRepository;
import com.agriinsight.backend.persistence.support.SqlTestResources;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import com.agriinsight.backend.shared.persistence.TenantContextState;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TenantRlsIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TENANT_C = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID PROFILE_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
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
    void missingAndInvalidTenantContextDenyWithoutCastingErrors() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(runtime, "SELECT count(*) FROM tenants")).isZero();
            execute(runtime, "SELECT set_config('app.tenant_id', 'not-a-uuid', false)");
            assertThat(count(runtime, """
                    SELECT count(*)
                    WHERE agriinsight_security.app_current_tenant_id() IS NULL
                    """)).isEqualTo(1);
            assertThat(count(runtime, "SELECT count(*) FROM user_profiles")).isZero();
            execute(runtime, "RESET app.tenant_id");
        }
    }

    @Test
    void tenantEqualityFiltersReadsAndWithCheckRejectsCrossTenantWrites() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_A);
            assertThat(count(runtime, "SELECT count(*) FROM user_profiles")).isEqualTo(1);
            assertThatThrownBy(() -> insertProfile(runtime, TENANT_B))
                    .isInstanceOf(SQLException.class);
            runtime.rollback();
        }
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM user_profiles
                    WHERE id = '30000000-0000-0000-0000-000000000001'
                    """)).isZero();
        }
    }

    @Test
    void transactionLocalContextDoesNotLeakThroughOneConnectionPool() throws Exception {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl(POSTGRESQL, "agriinsight"));
        configuration.setUsername(RUNTIME);
        configuration.setPassword(RUNTIME_PASSWORD);
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        configuration.setAutoCommit(false);
        try (HikariDataSource dataSource = new HikariDataSource(configuration)) {
            long firstBackend;
            try (var tenantAConnection = dataSource.getConnection()) {
                firstBackend = count(tenantAConnection, "SELECT pg_backend_pid()");
                setTenant(tenantAConnection, TENANT_A);
                assertThat(count(tenantAConnection, "SELECT count(*) FROM user_profiles")).isEqualTo(1);
                tenantAConnection.commit();
            }
            try (var reusedConnection = dataSource.getConnection()) {
                assertThat(count(reusedConnection, "SELECT pg_backend_pid()")).isEqualTo(firstBackend);
                assertThat(count(reusedConnection, "SELECT count(*) FROM user_profiles")).isZero();
                reusedConnection.commit();
            }
        }
    }

    @Test
    void principalLoaderUsesOneBoundConnectionAndDatabaseBackedPermissions() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl(POSTGRESQL, "agriinsight"));
        configuration.setUsername(RUNTIME);
        configuration.setPassword(RUNTIME_PASSWORD);
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        configuration.setConnectionTimeout(1_000);
        try (HikariDataSource dataSource = new HikariDataSource(configuration)) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            TenantPrincipalLoader loader = new TenantPrincipalLoader(
                    new PostgresIdentityBootstrapRepository(jdbcTemplate),
                    new PostgresTenantPrincipalRepository(jdbcTemplate),
                    new TenantContextBinder(jdbcTemplate),
                    new DataSourceTransactionManager(dataSource));

            var principal = loader.load(new ExternalIdentityClaims(
                    ISSUER,
                    "subject-a",
                    "Untrusted token display",
                    "untrusted@example.test",
                    "mfa"));

            assertThat(principal.profileId()).isEqualTo(PROFILE_A);
            assertThat(principal.tenantId()).isEqualTo(TENANT_A);
            assertThat(principal.tenantCode()).isEqualTo("TENANT-A");
            assertThat(principal.displayName()).contains("Admin A");
            assertThat(principal.email()).isEmpty();
            assertThat(principal.assurance()).contains("mfa");
            assertThat(principal.roles()).containsOnly(Role.TENANT_ADMIN);
            assertThat(principal.permissions())
                    .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT agriinsight_security.app_current_tenant_id()",
                    UUID.class))
                    .isNull();
        }
    }

    @Test
    void tenantScopedBusinessBoundaryBindsAndClearsOnePooledConnection() throws Throwable {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(jdbcUrl(POSTGRESQL, "agriinsight"));
        configuration.setUsername(RUNTIME);
        configuration.setPassword(RUNTIME_PASSWORD);
        configuration.setMaximumPoolSize(1);
        configuration.setMinimumIdle(1);
        configuration.setConnectionTimeout(1_000);
        try (HikariDataSource dataSource = new HikariDataSource(configuration)) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            TenantContextState contextState = new TenantContextState();
            TenantTransactionAspect aspect = new TenantTransactionAspect(
                    new TenantContextBinder(jdbcTemplate),
                    contextState,
                    new DataSourceTransactionManager(dataSource));
            TenantPrincipal principal = new TestPrincipal(PROFILE_A, TENANT_A);
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
            ProceedingJoinPoint service = mock(ProceedingJoinPoint.class);
            when(service.proceed()).thenAnswer(invocation -> {
                contextState.requireBound(TENANT_A);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT agriinsight_security.app_current_tenant_id()",
                        UUID.class)).isEqualTo(TENANT_A);
                return jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM user_profiles",
                        Long.class);
            });

            try {
                assertThat(aspect.withinTenantTransaction(service)).isEqualTo(1L);
                assertThat(contextState.currentTenantId()).isEmpty();
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT agriinsight_security.app_current_tenant_id()",
                        UUID.class)).isNull();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void bootstrapIsExactAndExternalIdentityRowsCannotBeEnumerated() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(runtime, bootstrapCountSql("subject-a"))).isEqualTo(1);
            assertThat(count(runtime, bootstrapCountSql("SUBJECT-A"))).isZero();
            assertThatThrownBy(() -> count(runtime, "SELECT count(*) FROM external_identities"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*) FROM agriinsight_security.read_external_identity(
                        '10000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000001',
                        '21000000-0000-0000-0000-000000000001'
                    )
                    """))
                    .isInstanceOfSatisfying(PSQLException.class, exception ->
                            assertThat(exception.getSQLState()).isEqualTo("42501"));
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_A);
            assertThat(count(runtime, """
                    SELECT count(*) FROM agriinsight_security.read_external_identity(
                        '10000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000001',
                        '21000000-0000-0000-0000-000000000001'
                    ) WHERE identity_issuer = 'https://identity.example.test/issuer'
                        AND identity_active
                        AND identity_version = 0
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*) FROM agriinsight_security.read_external_identity(
                        '10000000-0000-0000-0000-000000000002',
                        '20000000-0000-0000-0000-000000000002',
                        '21000000-0000-0000-0000-000000000002'
                    )
                    """))
                    .isInstanceOfSatisfying(PSQLException.class, exception ->
                            assertThat(exception.getSQLState()).isEqualTo("42501"));
            runtime.rollback();
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_A);
            assertThat(count(runtime, """
                    SELECT count(*) FROM agriinsight_security.link_external_identity(
                        '30000000-0000-0000-0000-000000000002',
                        '20000000-0000-0000-0000-000000000001',
                        'https://identity.example.test/issuer',
                        'subject-a-secondary'
                    ) AS linked WHERE linked
                    """)).isEqualTo(1);
            runtime.commit();
        }
    }

    @Test
    void inactiveIdentityRelinksToItsOriginalProfileAndAdvancesVersion() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_A);
            execute(runtime, """
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (
                        '20000000-0000-0000-0000-000000000005',
                        '10000000-0000-0000-0000-000000000001',
                        'Relink User A'
                    )
                    """);
            assertThat(count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.link_external_identity_versioned(
                        '21000000-0000-0000-0000-000000000005',
                        '20000000-0000-0000-0000-000000000005',
                        'https://identity.example.test/issuer',
                        'subject-a-relink'
                    )
                    WHERE identity_id = '21000000-0000-0000-0000-000000000005'
                      AND identity_version = 0
                    """)).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.unlink_external_identity_versioned(
                        '20000000-0000-0000-0000-000000000005',
                        '21000000-0000-0000-0000-000000000005'
                    ) AS identity_version
                    WHERE identity_version = 1
                    """)).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.link_external_identity_versioned(
                        '21000000-0000-0000-0000-000000000099',
                        '20000000-0000-0000-0000-000000000005',
                        'https://identity.example.test/issuer',
                        'subject-a-relink'
                    )
                    WHERE identity_id = '21000000-0000-0000-0000-000000000005'
                      AND identity_version = 2
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.link_external_identity_versioned(
                        '21000000-0000-0000-0000-000000000098',
                        '20000000-0000-0000-0000-000000000005',
                        'https://identity.example.test/issuer',
                        'subject-b'
                    )
                    """))
                    .isInstanceOfSatisfying(PSQLException.class, exception -> {
                        var serverError = exception.getServerErrorMessage();
                        assertThat(serverError).isNotNull();
                        assertThat(serverError.getConstraint())
                                .isEqualTo("ux_external_identities_issuer_subject");
                    });
            runtime.rollback();
        }
    }

    @Test
    void finalAdministratorIdentityCannotBeUnlinked() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_C);
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.unlink_external_identity(
                        '20000000-0000-0000-0000-000000000003',
                        '21000000-0000-0000-0000-000000000003'
                    ) AS unlinked
                    WHERE unlinked
                    """))
                    .isInstanceOfSatisfying(PSQLException.class, exception -> {
                        var serverError = exception.getServerErrorMessage();
                        assertThat(serverError).isNotNull();
                        assertThat(serverError.getConstraint())
                                .isEqualTo("tenant_last_active_admin_path");
                    });
            runtime.rollback();
        }

        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            setTenant(runtime, TENANT_C);
            execute(runtime, """
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (
                        '20000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000003',
                        'Backup Admin C'
                    )
                    """);
            execute(runtime, """
                    INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                    VALUES (
                        '22000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000003',
                        '20000000-0000-0000-0000-000000000004',
                        'TENANT_ADMIN'
                    )
                    """);
            assertThat(count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.link_external_identity(
                        '21000000-0000-0000-0000-000000000004',
                        '20000000-0000-0000-0000-000000000004',
                        'https://identity.example.test/issuer',
                        'subject-c-backup'
                    ) AS linked
                    WHERE linked
                    """)).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*)
                    FROM agriinsight_security.unlink_external_identity(
                        '20000000-0000-0000-0000-000000000003',
                        '21000000-0000-0000-0000-000000000003'
                    ) AS unlinked
                    WHERE unlinked
                    """)).isEqualTo(1);
            runtime.rollback();
        }
    }

    @Test
    void policyCatalogKeepsForceRlsAndRoleSpecificPermissivePolicies() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN (
                        'tenants', 'user_profiles', 'external_identities',
                        'user_roles', 'api_command_records', 'tenant_audit_events')
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(6);
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_policies
                    WHERE schemaname = 'public' AND permissive = 'PERMISSIVE'
                    """)).isEqualTo(59);
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_policies
                    WHERE tablename = 'external_identities'
                      AND 'agriinsight_runtime' = ANY(roles)
                    """)).isZero();
            assertThat(count(operator, """
                    SELECT count(*)
                    FROM pg_proc procedure
                    JOIN pg_namespace namespace ON namespace.oid = procedure.pronamespace
                    JOIN pg_roles owner_role ON owner_role.oid = procedure.proowner
                    WHERE namespace.nspname = 'agriinsight_security'
                      AND procedure.proname = 'assert_admin_path_remains'
                      AND procedure.prosecdef
                      AND procedure.proconfig = ARRAY['search_path=pg_catalog']::TEXT[]
                      AND owner_role.rolname = 'agriinsight_migrator'
                      AND has_function_privilege(
                            'agriinsight_runtime',
                            procedure.oid,
                            'EXECUTE')
                      AND NOT EXISTS (
                        SELECT 1
                        FROM aclexplode(COALESCE(
                            procedure.proacl,
                            acldefault('f', procedure.proowner)
                        )) AS privilege
                        WHERE privilege.grantee = 0
                          AND privilege.privilege_type = 'EXECUTE'
                      )
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*)
                    FROM pg_proc procedure
                    JOIN pg_namespace namespace ON namespace.oid = procedure.pronamespace
                    JOIN pg_roles owner_role ON owner_role.oid = procedure.proowner
                    WHERE namespace.nspname = 'agriinsight_security'
                      AND procedure.proname IN (
                        'link_external_identity_versioned',
                        'read_external_identity',
                        'unlink_external_identity_versioned'
                      )
                      AND procedure.prosecdef
                      AND procedure.proconfig = ARRAY['search_path=pg_catalog']::TEXT[]
                      AND owner_role.rolname = 'agriinsight_migrator'
                      AND has_function_privilege(
                            'agriinsight_runtime',
                            procedure.oid,
                            'EXECUTE')
                      AND NOT EXISTS (
                        SELECT 1
                        FROM aclexplode(COALESCE(
                            procedure.proacl,
                            acldefault('f', procedure.proowner)
                        )) AS privilege
                        WHERE privilege.grantee = 0
                          AND privilege.privilege_type = 'EXECUTE'
                      )
                    """)).isEqualTo(3);
        }
    }

    private static void setTenant(Connection connection, UUID tenantId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
            statement.setString(1, tenantId.toString());
            statement.executeQuery();
        }
    }

    private static void insertProfile(Connection connection, UUID tenantId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO user_profiles (id, tenant_id, display_name)
                VALUES ('30000000-0000-0000-0000-000000000001', ?, 'Cross tenant')
                """)) {
            statement.setObject(1, tenantId);
            statement.executeUpdate();
        }
    }

    private static String bootstrapCountSql(String subject) {
        return "SELECT count(*) FROM agriinsight_security.resolve_identity_bootstrap('"
                + ISSUER + "', '" + subject + "')";
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }

}
