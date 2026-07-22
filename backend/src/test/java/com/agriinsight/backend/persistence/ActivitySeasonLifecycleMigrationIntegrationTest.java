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
class ActivitySeasonLifecycleMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @Test
    void v11RejectsLiveActivitiesUnderTerminalSeasons() throws Exception {
        String database = "agriinsight_inconsistent_v10_activity";
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        bootstrapRoles(POSTGRESQL, database);

        Path migrationsThroughV10 = SqlTestResources.copyMigrationsThroughV10();
        try {
            var baseline = flyway(
                            POSTGRESQL,
                            database,
                            MIGRATOR,
                            MIGRATOR_PASSWORD,
                            "filesystem:" + migrationsThroughV10.toString().replace('\\', '/'))
                    .migrate();
            assertThat(baseline.migrationsExecuted).isEqualTo(10);
        } finally {
            SqlTestResources.deleteLegacyMigrations(migrationsThroughV10);
        }

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, inconsistentActivityFixture());
        }

        assertThatThrownBy(() -> migrate(POSTGRESQL, database))
                .hasMessageContaining("Cannot install activity season lifecycle guards");
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(scalar(operator, """
                    SELECT version FROM flyway_schema_history
                    WHERE success AND version IS NOT NULL
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("10");
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN ('seasons', 'activities')
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(2);
        }
    }

    private String inconsistentActivityFixture() {
        return """
                INSERT INTO tenants (id, code, display_name)
                VALUES ('10000000-0000-0000-0000-000000000093', 'INVALID-V10', 'Invalid V10');
                INSERT INTO farms (id, tenant_id, code, display_name)
                VALUES ('51000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000093', 'FARM-V10', 'Farm V10');
                INSERT INTO crops (id, tenant_id, code, display_name)
                VALUES ('51000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000093', 'CROP-V10', 'Crop V10');
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES ('51000000-0000-0000-0000-000000000003',
                        '10000000-0000-0000-0000-000000000093',
                        '51000000-0000-0000-0000-000000000001',
                        'FIELD-V10', 'Field V10', 2.0000);
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, started_on, ended_on,
                    planted_area_hectares, status)
                VALUES ('51000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000093',
                        '51000000-0000-0000-0000-000000000001',
                        '51000000-0000-0000-0000-000000000003',
                        '51000000-0000-0000-0000-000000000002',
                        'SEASON-V10', 'Season V10', DATE '2027-01-01', DATE '2027-12-31',
                        DATE '2027-01-01', DATE '2027-11-30', 1.0000, 'COMPLETED');
                INSERT INTO activity_types (tenant_id, code, display_name)
                VALUES ('10000000-0000-0000-0000-000000000093', 'IRRIGATION', 'Irrigation');
                INSERT INTO activities (
                    id, tenant_id, farm_id, field_id, season_id, activity_type_code,
                    code, title, planned_start_at, due_at, status)
                VALUES ('51000000-0000-0000-0000-000000000005',
                        '10000000-0000-0000-0000-000000000093',
                        '51000000-0000-0000-0000-000000000001',
                        '51000000-0000-0000-0000-000000000003',
                        '51000000-0000-0000-0000-000000000004',
                        'IRRIGATION', 'ACTIVITY-V10', 'Activity V10',
                        TIMESTAMPTZ '2027-06-01 01:00:00Z',
                        TIMESTAMPTZ '2027-06-01 03:00:00Z', 'PLANNED');
                """;
    }
}
