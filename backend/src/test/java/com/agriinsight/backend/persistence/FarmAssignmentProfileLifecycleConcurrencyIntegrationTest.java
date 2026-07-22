package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmAssignmentProfileLifecycleConcurrencyIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final Scenario ASSIGNMENT_FIRST = Scenario.numbered(21, "ASSIGNMENT-FIRST");
    private static final Scenario PROFILE_FIRST = Scenario.numbered(31, "PROFILE-FIRST");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        createScenario(ASSIGNMENT_FIRST);
        createScenario(PROFILE_FIRST);
    }

    @Test
    void assignmentCreationFirstBlocksThenRejectsProfileDeactivation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection assignment = tenantRuntimeConnection(POSTGRESQL)) {
            insertAssignment(assignment, ASSIGNMENT_FIRST);
            CountDownLatch started = new CountDownLatch(1);
            Future<Throwable> deactivation = executor.submit(() ->
                    deactivateProfile(ASSIGNMENT_FIRST, started));

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertBlocked(deactivation);
            assignment.commit();

            assertConstraint(
                    deactivation.get(10, TimeUnit.SECONDS),
                    "profile_deactivation_requires_revoked_farm_assignments");
        } finally {
            executor.shutdownNow();
        }
        assertScenario(ASSIGNMENT_FIRST, true, 1);
    }

    @Test
    void profileDeactivationFirstBlocksThenRejectsAssignmentCreation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection deactivation = tenantRuntimeConnection(POSTGRESQL)) {
            updateProfileInactive(deactivation, PROFILE_FIRST);
            CountDownLatch started = new CountDownLatch(1);
            Future<Throwable> insertion = executor.submit(() ->
                    insertAssignment(PROFILE_FIRST, started));

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertBlocked(insertion);
            deactivation.commit();

            assertConstraint(
                    insertion.get(10, TimeUnit.SECONDS),
                    "active_farm_assignment_requires_active_profile");
        } finally {
            executor.shutdownNow();
        }
        assertScenario(PROFILE_FIRST, false, 0);
    }

    private static void createScenario(Scenario scenario) throws Exception {
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES (?, ?, ?);
                    INSERT INTO farms (id, tenant_id, code, display_name)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setObject(1, scenario.profileId());
                statement.setObject(2, TENANT_ID);
                statement.setString(3, scenario.code() + " Manager");
                statement.setObject(4, scenario.farmId());
                statement.setObject(5, TENANT_ID);
                statement.setString(6, scenario.code());
                statement.setString(7, scenario.code() + " Farm");
                statement.execute();
            }
            connection.commit();
        }
    }

    private void insertAssignment(Connection connection, Scenario scenario) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, scenario.assignmentId());
            statement.setObject(2, TENANT_ID);
            statement.setObject(3, scenario.profileId());
            statement.setObject(4, scenario.farmId());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private Throwable deactivateProfile(Scenario scenario, CountDownLatch started) {
        started.countDown();
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            updateProfileInactive(connection, scenario);
            connection.commit();
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private void updateProfileInactive(Connection connection, Scenario scenario) throws Exception {
        try (var statement = connection.prepareStatement("""
                UPDATE user_profiles
                   SET active = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """)) {
            statement.setObject(1, scenario.profileId());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private Throwable insertAssignment(Scenario scenario, CountDownLatch started) {
        started.countDown();
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            insertAssignment(connection, scenario);
            connection.commit();
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private void assertBlocked(Future<?> operation) {
        assertThatThrownBy(() -> operation.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private void assertConstraint(Throwable failure, String expectedConstraint) {
        assertThat(failure).isInstanceOfSatisfying(PSQLException.class, exception ->
                assertThat(exception.getServerErrorMessage().getConstraint())
                        .isEqualTo(expectedConstraint));
    }

    private void assertScenario(Scenario scenario, boolean active, long assignments) throws Exception {
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            assertThat(count(connection, "SELECT count(*) FROM user_profiles WHERE id = '"
                    + scenario.profileId() + "' AND active = " + active)).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM user_farm_assignments WHERE id = '"
                    + scenario.assignmentId() + "' AND revoked_at IS NULL")).isEqualTo(assignments);
            connection.rollback();
        }
    }

    private record Scenario(UUID profileId, UUID farmId, UUID assignmentId, String code) {

        private static Scenario numbered(int number, String code) {
            String suffix = String.format("%012d", number);
            return new Scenario(
                    UUID.fromString("4d000000-0000-0000-0000-" + suffix),
                    UUID.fromString("4e000000-0000-0000-0000-" + suffix),
                    UUID.fromString("4f000000-0000-0000-0000-" + suffix),
                    code);
        }
    }
}
