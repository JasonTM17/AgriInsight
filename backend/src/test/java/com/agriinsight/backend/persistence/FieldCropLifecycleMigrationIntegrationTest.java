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
class FieldCropLifecycleMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @Test
    void v8RejectsPreExistingLiveSeasonsWithInactiveOrUndersizedParents() throws Exception {
        String database = "agriinsight_inconsistent_v7";
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        bootstrapRoles(POSTGRESQL, database);

        Path migrationsThroughV7 = SqlTestResources.copyMigrationsThroughV7();
        try {
            var baseline = flyway(
                            POSTGRESQL,
                            database,
                            MIGRATOR,
                            MIGRATOR_PASSWORD,
                            "filesystem:" + migrationsThroughV7.toString().replace('\\', '/'))
                    .migrate();
            assertThat(baseline.migrationsExecuted).isEqualTo(7);
        } finally {
            SqlTestResources.deleteLegacyMigrations(migrationsThroughV7);
        }

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, """
                    INSERT INTO tenants (id, code, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000081', 'INVALID-V7', 'Invalid V7')
                    """);
            execute(operator, """
                    INSERT INTO farms (id, tenant_id, code, display_name)
                    VALUES ('48000000-0000-0000-0000-000000000001',
                            '10000000-0000-0000-0000-000000000081', 'VALID-FARM', 'Valid Farm')
                    """);
            execute(operator, """
                    INSERT INTO crops (id, tenant_id, code, display_name, active)
                    VALUES ('48000000-0000-0000-0000-000000000002',
                            '10000000-0000-0000-0000-000000000081', 'INACTIVE-CROP', 'Inactive Crop', FALSE)
                    """);
            execute(operator, """
                    INSERT INTO fields (
                        id, tenant_id, farm_id, code, display_name, area_hectares, active)
                    VALUES ('48000000-0000-0000-0000-000000000003',
                            '10000000-0000-0000-0000-000000000081',
                            '48000000-0000-0000-0000-000000000001',
                            'INACTIVE-FIELD', 'Inactive Field', 1.0000, FALSE)
                    """);
            execute(operator, """
                    INSERT INTO seasons (
                        id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                        planned_start_date, planned_end_date, planted_area_hectares)
                    VALUES ('48000000-0000-0000-0000-000000000004',
                            '10000000-0000-0000-0000-000000000081',
                            '48000000-0000-0000-0000-000000000001',
                            '48000000-0000-0000-0000-000000000003',
                            '48000000-0000-0000-0000-000000000002',
                            'LIVE-SEASON', 'Live Season', DATE '2027-01-01', DATE '2027-12-31', 2.0000)
                    """);
        }

        assertThatThrownBy(() -> migrate(POSTGRESQL, database))
                .hasMessageContaining("Cannot install field and crop lifecycle guards");
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(scalar(operator, """
                    SELECT version FROM flyway_schema_history
                    WHERE success AND version IS NOT NULL
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("7");
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN ('fields', 'crops', 'seasons', 'activities')
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(4);
        }
    }
}
