package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.MIGRATOR;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.MIGRATOR_PASSWORD;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.flyway;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.scalar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.persistence.support.SqlTestResources;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmAssignmentProfileLifecycleMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @Test
    void v10RejectsInactiveProfilesWithActiveFarmAssignments() throws Exception {
        String database = "agriinsight_inconsistent_v9_assignments";
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        bootstrapRoles(POSTGRESQL, database);

        Path migrationsThroughV9 = SqlTestResources.copyMigrationsThroughV9();
        try {
            var baseline = flyway(
                            POSTGRESQL,
                            database,
                            MIGRATOR,
                            MIGRATOR_PASSWORD,
                            "filesystem:" + migrationsThroughV9.toString().replace('\\', '/'))
                    .migrate();
            assertThat(baseline.migrationsExecuted).isEqualTo(9);
        } finally {
            SqlTestResources.deleteLegacyMigrations(migrationsThroughV9);
        }

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, """
                    INSERT INTO tenants (id, code, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000092', 'INVALID-V9', 'Invalid V9');
                    INSERT INTO user_profiles (id, tenant_id, display_name, active)
                    VALUES ('4b000000-0000-0000-0000-000000000011',
                            '10000000-0000-0000-0000-000000000092',
                            'Inactive Manager', FALSE);
                    INSERT INTO farms (id, tenant_id, code, display_name)
                    VALUES ('4b000000-0000-0000-0000-000000000012',
                            '10000000-0000-0000-0000-000000000092',
                            'VALID-FARM', 'Valid Farm');
                    INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                    VALUES ('4b000000-0000-0000-0000-000000000013',
                            '10000000-0000-0000-0000-000000000092',
                            '4b000000-0000-0000-0000-000000000011',
                            '4b000000-0000-0000-0000-000000000012')
                    """);
        }

        assertThatThrownBy(() -> migrate(POSTGRESQL, database))
                .hasMessageContaining("Cannot install farm assignment profile lifecycle guards");
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(scalar(operator, """
                    SELECT version FROM flyway_schema_history
                    WHERE success AND version IS NOT NULL
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("9");
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN ('user_profiles', 'user_farm_assignments')
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(2);
        }
    }
}
