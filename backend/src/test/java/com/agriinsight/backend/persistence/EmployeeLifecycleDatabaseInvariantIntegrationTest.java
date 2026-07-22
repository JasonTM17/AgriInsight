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
class EmployeeLifecycleDatabaseInvariantIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void activeResponsibilitiesBlockEmployeeDeactivation() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE employees SET active = FALSE
                WHERE id = '41000000-0000-0000-0000-000000000004'
                """);
    }

    @Test
    void inactiveEmployeesCannotReceiveLiveResponsibilities() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO employees (id, tenant_id, code, display_name, active)
                VALUES ('4a000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        'INACTIVE-RESPONSIBLE', 'Inactive Responsible', FALSE);
                INSERT INTO fields (
                    id, tenant_id, farm_id, code, display_name, area_hectares,
                    responsible_employee_id)
                VALUES ('4a000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000001',
                        'INACTIVE-EMP-FIELD', 'Inactive Employee Field', 1.0000,
                        '4a000000-0000-0000-0000-000000000001')
                """);
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO employees (id, tenant_id, code, display_name, active)
                VALUES ('4a000000-0000-0000-0000-000000000003',
                        '10000000-0000-0000-0000-000000000041',
                        'INACTIVE-ASSIGNEE', 'Inactive Assignee', FALSE);
                INSERT INTO activity_assignees (id, tenant_id, activity_id, employee_id)
                VALUES ('4a000000-0000-0000-0000-000000000004',
                        '10000000-0000-0000-0000-000000000041',
                        '41000000-0000-0000-0000-000000000007',
                        '4a000000-0000-0000-0000-000000000003')
                """);
    }

    @Test
    void clearedResponsibilitiesAllowEmployeeDeactivation() throws Exception {
        try (Connection runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    UPDATE fields
                       SET responsible_employee_id = NULL,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000003';
                    UPDATE activity_assignees
                       SET revoked_at = CURRENT_TIMESTAMP,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000009';
                    UPDATE employees
                       SET active = FALSE,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000004'
                    """);
            assertThat(count(runtime, """
                    SELECT count(*) FROM employees
                    WHERE id = '41000000-0000-0000-0000-000000000004'
                      AND NOT active AND version = 1
                    """)).isEqualTo(1);
            runtime.rollback();
        }
    }
}
