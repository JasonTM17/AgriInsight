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

import com.agriinsight.backend.persistence.support.SqlTestResources;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class TenantRlsIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("10000000-0000-0000-0000-000000000002");
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
    void bootstrapIsExactAndExternalIdentityRowsCannotBeEnumerated() throws Exception {
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(runtime, bootstrapCountSql("subject-a"))).isEqualTo(1);
            assertThat(count(runtime, bootstrapCountSql("SUBJECT-A"))).isZero();
            assertThatThrownBy(() -> count(runtime, "SELECT count(*) FROM external_identities"))
                    .isInstanceOf(SQLException.class);
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
                    """)).isEqualTo(14);
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_policies
                    WHERE tablename = 'external_identities'
                      AND 'agriinsight_runtime' = ANY(roles)
                    """)).isZero();
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

}
