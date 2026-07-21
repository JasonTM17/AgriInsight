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
class FarmLifecycleMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @Test
    void v7RejectsInactiveFarmsWithPreExistingLiveDependencies() throws Exception {
        String database = "agriinsight_inconsistent_v6";
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "CREATE DATABASE " + database);
        }
        bootstrapRoles(POSTGRESQL, database);

        Path migrationsThroughV6 = SqlTestResources.copyMigrationsThroughV6();
        try {
            var baseline = flyway(
                            POSTGRESQL,
                            database,
                            MIGRATOR,
                            MIGRATOR_PASSWORD,
                            "filesystem:" + migrationsThroughV6.toString().replace('\\', '/'))
                    .migrate();
            assertThat(baseline.migrationsExecuted).isEqualTo(6);
        } finally {
            SqlTestResources.deleteLegacyMigrations(migrationsThroughV6);
        }

        try (var operator = operatorConnection(POSTGRESQL, database)) {
            execute(operator, """
                    INSERT INTO tenants (id, code, display_name)
                    VALUES ('10000000-0000-0000-0000-000000000071', 'INVALID-V6', 'Invalid V6')
                    """);
            execute(operator, """
                    INSERT INTO farms (id, tenant_id, code, display_name)
                    VALUES ('47000000-0000-0000-0000-000000000001',
                            '10000000-0000-0000-0000-000000000071', 'INVALID-FARM', 'Invalid Farm')
                    """);
            execute(operator, """
                    INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                    VALUES ('47000000-0000-0000-0000-000000000002',
                            '10000000-0000-0000-0000-000000000071',
                            '47000000-0000-0000-0000-000000000001',
                            'LIVE-FIELD', 'Live Field', 1.0000)
                    """);
            execute(operator, """
                    UPDATE farms SET active = FALSE
                    WHERE id = '47000000-0000-0000-0000-000000000001'
                    """);
        }

        assertThatThrownBy(() -> migrate(POSTGRESQL, database))
                .hasMessageContaining("inactive farm has live dependents");
        try (var operator = operatorConnection(POSTGRESQL, database)) {
            assertThat(scalar(operator, """
                    SELECT version FROM flyway_schema_history
                    WHERE success AND version IS NOT NULL
                    ORDER BY installed_rank DESC LIMIT 1
                    """)).isEqualTo("6");
            assertThat(count(operator, """
                    SELECT count(*) FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'public'
                      AND relation.relname IN (
                        'farms', 'fields', 'seasons', 'activities', 'user_farm_assignments')
                      AND relation.relrowsecurity
                      AND relation.relforcerowsecurity
                    """)).isEqualTo(5);
        }
    }
}
