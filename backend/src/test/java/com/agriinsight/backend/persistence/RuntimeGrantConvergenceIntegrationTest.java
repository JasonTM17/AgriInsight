package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migratorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RuntimeGrantConvergenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void migrateDatabase() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
    }

    @Test
    void repeatableGrantsConvergeFromLegacyTableWideUpdate() throws Exception {
        try (var migrator = migratorConnection(POSTGRESQL, "agriinsight")) {
            execute(migrator, """
                    GRANT UPDATE ON seasons, user_farm_assignments, activities,
                        activity_assignees, inventory_transactions
                    TO agriinsight_runtime
                    """);
            execute(
                    migrator,
                    com.agriinsight.backend.persistence.support.SqlTestResources.projectFile(
                            "backend/src/main/resources/db/migration/R__tenant_rls_helpers_and_grants.sql"));
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('seasons'), ('user_farm_assignments'),
                            ('activities'), ('activity_assignees'),
                            ('inventory_transactions')) AS scoped(table_name)
                     WHERE NOT has_table_privilege(
                            'agriinsight_runtime', 'public.' || table_name, 'UPDATE')
                    """)).isEqualTo(5);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('seasons', 'status'),
                            ('user_farm_assignments', 'revoked_at'),
                            ('activities', 'title'),
                            ('activity_assignees', 'revoked_at'),
                            ('inventory_transactions', 'version'))
                           AS allowed(table_name, column_name)
                     WHERE has_column_privilege(
                            'agriinsight_runtime', 'public.' || table_name, column_name, 'UPDATE')
                    """)).isEqualTo(5);
        }
    }
}
