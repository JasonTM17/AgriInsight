package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.InventoryQueryPlanTestSupport.explain;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CostQueryPlanIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void periodAndCategoryQueriesUseTenantLeadingIndexes() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, "SET enable_seqscan = off");
            assertThat(explain(operator, """
                    SELECT id FROM operating_cost_entries
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND occurred_at >= TIMESTAMPTZ '2027-01-01T00:00:00Z'
                       AND occurred_at < TIMESTAMPTZ '2028-01-01T00:00:00Z'
                     ORDER BY occurred_at DESC, id DESC LIMIT 101
                    """)).anyMatch(line -> line.contains(
                            "ix_operating_cost_entries_tenant_occurred"));
            assertThat(explain(operator, """
                    SELECT id FROM operating_cost_entries
                     WHERE tenant_id = '10000000-0000-0000-0000-000000000041'
                       AND category_code = 'LABOR'
                       AND occurred_at >= TIMESTAMPTZ '2027-01-01T00:00:00Z'
                     ORDER BY occurred_at DESC, id DESC LIMIT 101
                    """)).anyMatch(line -> line.contains(
                            "ix_operating_cost_entries_tenant_category_occurred"));
        }
    }
}
