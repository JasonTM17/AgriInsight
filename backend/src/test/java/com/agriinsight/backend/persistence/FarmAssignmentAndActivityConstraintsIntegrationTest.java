package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.assertRuntimeStatementRejected;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmAssignmentAndActivityConstraintsIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void activeAssignmentHistoryRejectsDuplicatesReparentingAndUnrevocation() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                VALUES ('41000000-0000-0000-0000-000000000018',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000005',
                        '41000000-0000-0000-0000-000000000001')
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO activity_assignees (id, tenant_id, activity_id, employee_id)
                VALUES ('41000000-0000-0000-0000-000000000019',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000007',
                        '41000000-0000-0000-0000-000000000004')
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE user_farm_assignments SET farm_id = farm_id
                WHERE id = '41000000-0000-0000-0000-000000000008'
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE activity_assignees SET employee_id = employee_id
                WHERE id = '41000000-0000-0000-0000-000000000009'
                """);

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    UPDATE user_farm_assignments
                       SET revoked_at = CURRENT_TIMESTAMP,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000008'
                    """);
            assertThat(count(runtime, """
                    SELECT count(*) FROM user_farm_assignments
                    WHERE id = '41000000-0000-0000-0000-000000000008'
                      AND revoked_at IS NOT NULL AND version = 1
                    """)).isEqualTo(1);
            assertThatThrownBy(() -> execute(runtime, """
                    UPDATE user_farm_assignments
                       SET revoked_at = NULL,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000008'
                    """)).isInstanceOf(SQLException.class);
            runtime.rollback();
        }
    }

    @Test
    void activityLifecycleRejectsInvalidStateAndParentRewrites() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO activities (
                    id, tenant_id, farm_id, field_id, season_id, activity_type_code,
                    code, title, planned_start_at, due_at, started_at, status)
                VALUES (
                    '41000000-0000-0000-0000-000000000017',
                    '10000000-0000-0000-0000-000000000041',
                    '41000000-0000-0000-0000-000000000001',
                    '41000000-0000-0000-0000-000000000003',
                    '41000000-0000-0000-0000-000000000006',
                    'HARVEST', 'BAD-COMPLETED', 'Invalid completed activity',
                    TIMESTAMPTZ '2027-09-03 01:00:00Z', TIMESTAMPTZ '2027-09-03 03:00:00Z',
                    TIMESTAMPTZ '2027-09-03 01:10:00Z', 'COMPLETED')
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE activities SET season_id = season_id
                WHERE id = '41000000-0000-0000-0000-000000000007'
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE seasons SET crop_id = crop_id
                WHERE id = '41000000-0000-0000-0000-000000000006'
                """);

        try (var runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    UPDATE activities
                       SET title = 'Updated harvest A',
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000007'
                    """);
            assertThat(count(runtime, """
                    SELECT count(*) FROM activities
                    WHERE id = '41000000-0000-0000-0000-000000000007'
                      AND title = 'Updated harvest A' AND version = 1
                    """)).isEqualTo(1);
            runtime.rollback();
        }
    }

    @Test
    void cancelledActivityRejectsCancellationBeforeStart() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO activities (
                    id, tenant_id, farm_id, field_id, season_id, activity_type_code,
                    code, title, planned_start_at, due_at, started_at, cancelled_at, status)
                VALUES (
                    '41000000-0000-0000-0000-000000000020',
                    '10000000-0000-0000-0000-000000000041',
                    '41000000-0000-0000-0000-000000000001',
                    '41000000-0000-0000-0000-000000000003',
                    '41000000-0000-0000-0000-000000000006',
                    'HARVEST', 'BAD-CANCELLATION', 'Invalid cancelled activity',
                    TIMESTAMPTZ '2027-09-01 01:00:00Z', TIMESTAMPTZ '2027-09-03 03:00:00Z',
                    TIMESTAMPTZ '2027-09-02 01:00:00Z', TIMESTAMPTZ '2027-09-01 12:00:00Z',
                    'CANCELLED')
                """);
    }
}
