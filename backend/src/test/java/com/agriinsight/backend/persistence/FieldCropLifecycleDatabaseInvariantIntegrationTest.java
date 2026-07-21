package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.assertRuntimeStatementRejected;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FieldCropLifecycleDatabaseInvariantIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void liveSeasonsRequireActiveFieldAndCropWithinFieldCapacity() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO fields (
                    id, tenant_id, farm_id, code, display_name, area_hectares, active)
                VALUES ('49000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        'INACTIVE-FIELD-GUARD', 'Inactive Field Guard', 2.0000, FALSE);
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares)
                VALUES ('49000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        '49000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000002',
                        'FIELD-GUARD-SEASON', 'Field Guard Season',
                        DATE '2028-01-01', DATE '2028-12-31', 1.0000)
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO crops (id, tenant_id, code, display_name, active)
                VALUES ('49000000-0000-0000-0000-000000000003',
                        '10000000-0000-0000-0000-000000000041',
                        'INACTIVE-CROP-GUARD', 'Inactive Crop Guard', FALSE);
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares)
                VALUES ('49000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000003',
                        '49000000-0000-0000-0000-000000000003',
                        'CROP-GUARD-SEASON', 'Crop Guard Season',
                        DATE '2028-01-01', DATE '2028-12-31', 1.0000)
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares)
                VALUES ('49000000-0000-0000-0000-000000000005',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000003',
                        '41000000-0000-0000-0000-000000000002',
                        'OVERSIZED-SEASON', 'Oversized Season',
                        DATE '2028-01-01', DATE '2028-12-31', 12.5001)
                """);
    }

    @Test
    void liveDependenciesBlockParentDeactivationAndFieldAreaShrink() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE fields SET active = FALSE
                WHERE id = '41000000-0000-0000-0000-000000000003'
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE crops SET active = FALSE
                WHERE id = '41000000-0000-0000-0000-000000000002'
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE fields SET area_hectares = 9.9999
                WHERE id = '41000000-0000-0000-0000-000000000003'
                """);
    }

    @Test
    void terminalHistoryDoesNotPreventFieldOrCropDeactivation() throws Exception {
        try (Connection runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    INSERT INTO crops (id, tenant_id, code, display_name)
                    VALUES ('49000000-0000-0000-0000-000000000010',
                            '10000000-0000-0000-0000-000000000041',
                            'TERMINAL-CROP', 'Terminal Crop');
                    INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                    VALUES ('49000000-0000-0000-0000-000000000011',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000001',
                            'TERMINAL-FIELD', 'Terminal Field', 3.0000);
                    INSERT INTO seasons (
                        id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                        planned_start_date, planned_end_date, started_on, ended_on,
                        planted_area_hectares, status)
                    VALUES ('49000000-0000-0000-0000-000000000012',
                            '10000000-0000-0000-0000-000000000041',
                            '41000000-0000-0000-0000-000000000001',
                            '49000000-0000-0000-0000-000000000011',
                            '49000000-0000-0000-0000-000000000010',
                            'TERMINAL-SEASON', 'Terminal Season', DATE '2026-01-01', DATE '2026-06-30',
                            DATE '2026-01-02', DATE '2026-06-29', 2.0000, 'COMPLETED');
                    UPDATE fields SET active = FALSE
                    WHERE id = '49000000-0000-0000-0000-000000000011';
                    UPDATE crops SET active = FALSE
                    WHERE id = '49000000-0000-0000-0000-000000000010'
                    """);
            assertThat(count(runtime, """
                    SELECT count(*) FROM fields
                    WHERE id = '49000000-0000-0000-0000-000000000011' AND NOT active
                    """)).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*) FROM crops
                    WHERE id = '49000000-0000-0000-0000-000000000010' AND NOT active
                    """)).isEqualTo(1);
            runtime.rollback();
        }
    }
}
