package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.IDENTITY_DEFINER;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.MIGRATOR;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.MIGRATOR_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.RUNTIME;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.flyway;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.SQLException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FlywayMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();
    private static MigrateResult initialMigration;

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        initialMigration = migrate(POSTGRESQL, "agriinsight");
    }

    @Test
    void freshPostgresqlAppliesAllMigrationsAndValidates() throws Exception {
        assertThat(initialMigration.success).isTrue();
        assertThat(initialMigration.migrationsExecuted).isEqualTo(10);
        assertThat(migrate(POSTGRESQL, "agriinsight").migrationsExecuted).isZero();
        try (var connection = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(scalar(connection, """
                    SELECT version FROM flyway_schema_history
                    WHERE success AND version IS NOT NULL
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("9");
            assertThat(count(connection, "SELECT count(*) FROM permissions")).isEqualTo(19);
            assertThat(count(connection, "SELECT count(*) FROM roles")).isEqualTo(7);
        }
    }

    @Test
    void phaseThreeDatabaseBackfillsActivityTypesDuringUpgrade() throws Exception {
        String database = "agriinsight_phase_three_upgrade";
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        bootstrapRoles(POSTGRESQL, database);

        Path phaseThreeMigrations =
                com.agriinsight.backend.persistence.support.SqlTestResources.copyMigrationsThroughV4();
        try {
            var baseline = flyway(
                            POSTGRESQL,
                            database,
                            MIGRATOR,
                            MIGRATOR_PASSWORD,
                            "filesystem:" + phaseThreeMigrations.toString().replace('\\', '/'))
                    .migrate();
            assertThat(baseline.migrationsExecuted).isEqualTo(4);
        } finally {
            com.agriinsight.backend.persistence.support.SqlTestResources.deleteLegacyMigrations(
                    phaseThreeMigrations);
        }

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, """
                    INSERT INTO tenants (id, code, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000061', 'UPGRADE-A', 'Upgrade tenant')
                    """);
        }

        assertThat(migrate(POSTGRESQL, database).migrationsExecuted).isEqualTo(6);
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(count(operator, """
                    SELECT count(*) FROM activity_types
                    WHERE tenant_id = '10000000-0000-0000-0000-000000000061'
                    """)).isEqualTo(8);
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname = 'tenants'
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(1);
        }
    }

    @Test
    void databaseRolesAndBootstrapFunctionAreLeastPrivilege() throws Exception {
        migrate(POSTGRESQL, "agriinsight");
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertSafeRole(operator, MIGRATOR, true, true);
            assertSafeRole(operator, RUNTIME, true, true);
            assertSafeRole(operator, IDENTITY_DEFINER, false, false);
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_auth_members membership
                    JOIN pg_roles granted_role ON granted_role.oid = membership.roleid
                    JOIN pg_roles member_role ON member_role.oid = membership.member
                    WHERE granted_role.rolname = 'agriinsight_identity_definer'
                      AND member_role.rolname = 'agriinsight_migrator'
                      AND NOT membership.admin_option
                      AND NOT membership.inherit_option
                      AND membership.set_option
                    """)).isEqualTo(1);
            assertThat(scalar(operator, """
                    SELECT owner.rolname FROM pg_proc routine
                    JOIN pg_namespace namespace ON namespace.oid = routine.pronamespace
                    JOIN pg_roles owner ON owner.oid = routine.proowner
                    WHERE namespace.nspname = 'agriinsight_security'
                      AND routine.proname = 'resolve_identity_bootstrap'
                    """)).isEqualTo(IDENTITY_DEFINER);
        }
        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            assertThatThrownBy(() -> count(runtime, "SELECT count(*) FROM external_identities"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void assertSafeRole(
            java.sql.Connection connection,
            String role,
            boolean expectedLogin,
            boolean expectedInherit) throws SQLException {
        assertThat(count(connection, """
                SELECT count(*) FROM pg_roles
                WHERE rolname = '%s'
                  AND rolcanlogin = %s
                  AND rolinherit = %s
                  AND NOT rolsuper
                  AND NOT rolcreatedb
                  AND NOT rolcreaterole
                  AND NOT rolreplication
                  AND NOT rolbypassrls
                """.formatted(role, expectedLogin, expectedInherit))).isEqualTo(1);
    }
}
