package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmLifecycleDatabaseInvariantIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID FIXTURE_FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_FIRST_FARM_ID = UUID.fromString("49000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_FIRST_FIELD_ID = UUID.fromString("49000000-0000-0000-0000-000000000002");
    private static final UUID PARENT_FIRST_FARM_ID = UUID.fromString("49000000-0000-0000-0000-000000000003");
    private static final UUID PARENT_FIRST_FIELD_ID = UUID.fromString("49000000-0000-0000-0000-000000000004");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @Test
    void restrictedRuntimeCannotBypassLiveDependencyGuard() throws Exception {
        try (Connection runtime = tenantRuntimeConnection(POSTGRESQL)) {
            assertThatThrownBy(() -> deactivate(runtime, FIXTURE_FARM_ID))
                    .isInstanceOf(SQLException.class)
                    .satisfies(this::assertCheckViolation);
        }
        assertFarmState(FIXTURE_FARM_ID, true, 1);
    }

    @Test
    void childFirstWriteBlocksThenRejectsDirectDeactivation() throws Exception {
        createFarm(CHILD_FIRST_FARM_ID, "CHILD-FIRST");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection child = tenantRuntimeConnection(POSTGRESQL);
                Connection parent = tenantRuntimeConnection(POSTGRESQL)) {
            insertField(child, CHILD_FIRST_FARM_ID, CHILD_FIRST_FIELD_ID, "CHILD-FIRST-FIELD");
            Future<Void> deactivation = executor.submit(() -> {
                deactivate(parent, CHILD_FIRST_FARM_ID);
                return null;
            });
            assertThatThrownBy(() -> deactivation.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            child.commit();
            assertFutureCheckViolation(deactivation);
            parent.rollback();
        } finally {
            executor.shutdownNow();
        }
        assertFarmState(CHILD_FIRST_FARM_ID, true, 1);
    }

    @Test
    void parentFirstDeactivationBlocksThenRejectsLiveChildWrite() throws Exception {
        createFarm(PARENT_FIRST_FARM_ID, "PARENT-FIRST");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection parent = tenantRuntimeConnection(POSTGRESQL);
                Connection child = tenantRuntimeConnection(POSTGRESQL)) {
            deactivate(parent, PARENT_FIRST_FARM_ID);
            Future<Void> insertion = executor.submit(() -> {
                insertField(child, PARENT_FIRST_FARM_ID, PARENT_FIRST_FIELD_ID, "PARENT-FIRST-FIELD");
                child.commit();
                return null;
            });
            assertThatThrownBy(() -> insertion.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            parent.commit();
            assertFutureCheckViolation(insertion);
            child.rollback();
        } finally {
            executor.shutdownNow();
        }
        assertFarmState(PARENT_FIRST_FARM_ID, false, 0);
    }

    private void createFarm(UUID farmId, String code) throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO farms (id, tenant_id, code, display_name)
                    VALUES ('%s', '%s', '%s', '%s')
                    """.formatted(farmId, TENANT_ID, code, code));
        }
    }

    private void insertField(Connection connection, UUID farmId, UUID fieldId, String code)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES (?, ?, ?, ?, ?, 1.0000)
                """)) {
            statement.setObject(1, fieldId);
            statement.setObject(2, TENANT_ID);
            statement.setObject(3, farmId);
            statement.setString(4, code);
            statement.setString(5, code);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void deactivate(Connection connection, UUID farmId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE farms
                   SET active = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND id = ?
                """)) {
            statement.setObject(1, TENANT_ID);
            statement.setObject(2, farmId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void assertFutureCheckViolation(Future<Void> operation) {
        assertThatThrownBy(() -> operation.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(exception -> assertCheckViolation(exception.getCause()));
    }

    private void assertCheckViolation(Throwable exception) {
        assertThat(exception).isInstanceOf(SQLException.class);
        assertThat(((SQLException) exception).getSQLState()).isEqualTo("23514");
    }

    private void assertFarmState(UUID farmId, boolean active, int expectedFields) throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM farms
                    WHERE id = '%s' AND active = %s
                    """.formatted(farmId, active))).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM fields WHERE farm_id = '%s'
                    """.formatted(farmId))).isEqualTo(expectedFields);
        }
    }
}
