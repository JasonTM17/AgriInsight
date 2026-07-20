package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.MIGRATOR;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.connection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.flyway;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.persistence.support.SqlTestResources;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class OwnershipAdoptionIntegrationTest {

    private static final String LEGACY_OWNER = "agriinsight_legacy_owner";
    private static final String LEGACY_PASSWORD = "legacy-integration-only";
    private static final String UNPRIVILEGED = "agriinsight_adoption_unprivileged";
    private static final String UNPRIVILEGED_PASSWORD = "adoption-integration-only";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void createClusterRoles() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    CREATE ROLE agriinsight_legacy_owner LOGIN PASSWORD 'legacy-integration-only'
                        NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
                    CREATE ROLE agriinsight_adoption_unprivileged LOGIN PASSWORD 'adoption-integration-only'
                        NOSUPERUSER INHERIT NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
                    """);
        }
    }

    @Test
    void allowlistedV1ToV3DatabaseAdoptsThenMigratesWithForceRls() throws Exception {
        String database = "agriinsight_legacy_safe";
        prepareLegacyDatabase(database, false);

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, adoptionSql());
            assertThat(ownerOf(operator, "tenants")).isEqualTo(MIGRATOR);
            assertThat(ownerOf(operator, "flyway_schema_history")).isEqualTo(MIGRATOR);
        }

        var migration = migrate(POSTGRESQL, database);
        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(4);
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count(
                    operator,
                    """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN (
                        'tenants', 'user_profiles', 'external_identities',
                        'user_roles', 'api_command_records', 'tenant_audit_events')
                      AND relation.relrowsecurity AND relation.relforcerowsecurity
                    """)).isEqualTo(6);
        }
    }

    @Test
    void unexpectedLegacyObjectFailsBeforeOwnershipChanges() throws Exception {
        String database = "agriinsight_legacy_unexpected";
        prepareLegacyDatabase(database, true);

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThatThrownBy(() -> execute(operator, adoptionSql()))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Unexpected V1-V3 relation refused");
            assertThat(ownerOf(operator, "tenants")).isEqualTo(LEGACY_OWNER);
        }
    }

    @Test
    void adoptionConnectionWithoutSetRolePrivilegeFailsClosed() throws Exception {
        String database = "agriinsight_legacy_unprivileged";
        prepareLegacyDatabase(database, false);
        try (var unprivileged = connection(
                POSTGRESQL,
                database,
                UNPRIVILEGED,
                UNPRIVILEGED_PASSWORD)) {
            assertThatThrownBy(() -> execute(unprivileged, adoptionSql()))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("cannot SET ROLE to legacy owner");
        }
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(ownerOf(operator, "tenants")).isEqualTo(LEGACY_OWNER);
        }
    }

    @Test
    void roleGateRejectsUnsafeRuntimeAttributes() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "ALTER ROLE agriinsight_runtime BYPASSRLS");
            try {
                assertThatThrownBy(() -> execute(
                        operator,
                        SqlTestResources.projectFile("backend/ops/postgres/bootstrap-roles.sql")))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("unsafe or unexpected attributes");
            } finally {
                execute(operator, "ALTER ROLE agriinsight_runtime NOBYPASSRLS");
            }
        }
    }

    private static void prepareLegacyDatabase(String database, boolean unexpectedObject) throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, "GRANT CONNECT, CREATE ON DATABASE " + database + " TO " + LEGACY_OWNER);
            execute(operator, "GRANT CONNECT ON DATABASE " + database + " TO " + UNPRIVILEGED);
            execute(operator, "REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            execute(operator, "GRANT USAGE, CREATE ON SCHEMA public TO " + LEGACY_OWNER);
        }

        Path legacyMigrations = SqlTestResources.copyLegacyMigrations();
        try {
            var result = flyway(
                    POSTGRESQL,
                    database,
                    LEGACY_OWNER,
                    LEGACY_PASSWORD,
                    "filesystem:" + legacyMigrations.toString().replace('\\', '/')).migrate();
            assertThat(result.migrationsExecuted).isEqualTo(3);
        } finally {
            SqlTestResources.deleteLegacyMigrations(legacyMigrations);
        }

        if (unexpectedObject) {
            try (var legacy = connection(POSTGRESQL, database, LEGACY_OWNER, LEGACY_PASSWORD)) {
                execute(legacy, "CREATE TABLE unexpected_shared_table (id INTEGER PRIMARY KEY)");
            }
        }
        bootstrapRoles(POSTGRESQL, database);
    }

    private static String adoptionSql() throws Exception {
        return SqlTestResources.renderPsqlScript(
                "backend/ops/postgres/adopt-schema-ownership.sql",
                Map.of("legacy_owner", LEGACY_OWNER, "migration_owner", MIGRATOR));
    }

    private static String ownerOf(java.sql.Connection connection, String table) throws SQLException {
        return scalar(connection, """
                SELECT owner.rolname FROM pg_class relation
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                JOIN pg_roles owner ON owner.oid = relation.relowner
                WHERE namespace.nspname = 'public' AND relation.relname = '%s'
                """.formatted(table));
    }
}
