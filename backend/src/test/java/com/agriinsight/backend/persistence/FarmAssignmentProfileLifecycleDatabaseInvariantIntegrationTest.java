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
class FarmAssignmentProfileLifecycleDatabaseInvariantIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void activeFarmAssignmentBlocksProfileDeactivation() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                UPDATE user_profiles SET active = FALSE
                WHERE id = '41000000-0000-0000-0000-000000000005'
                """);
    }

    @Test
    void inactiveProfileCannotReceiveActiveFarmAssignment() throws Exception {
        assertRuntimeStatementRejected(POSTGRESQL, """
                INSERT INTO user_profiles (id, tenant_id, display_name, active)
                VALUES ('4b000000-0000-0000-0000-000000000001',
                        '10000000-0000-0000-0000-000000000041',
                        'Inactive Farm Manager', FALSE);
                INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                VALUES ('4b000000-0000-0000-0000-000000000002',
                        '10000000-0000-0000-0000-000000000041',
                        '4b000000-0000-0000-0000-000000000001',
                        '41000000-0000-0000-0000-000000000001')
                """);
    }

    @Test
    void revokedFarmAssignmentAllowsProfileDeactivation() throws Exception {
        try (Connection runtime = tenantRuntimeConnection(POSTGRESQL)) {
            execute(runtime, """
                    UPDATE user_farm_assignments
                       SET revoked_at = CURRENT_TIMESTAMP,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000008';
                    UPDATE user_profiles
                       SET active = FALSE,
                           version = version + 1,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE id = '41000000-0000-0000-0000-000000000005'
                    """);
            assertThat(count(runtime, """
                    SELECT count(*) FROM user_profiles
                    WHERE id = '41000000-0000-0000-0000-000000000005'
                      AND NOT active AND version = 1
                    """)).isEqualTo(1);
            runtime.rollback();
        }
    }
}
