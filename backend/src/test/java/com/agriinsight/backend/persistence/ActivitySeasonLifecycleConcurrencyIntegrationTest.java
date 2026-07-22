package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
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
class ActivitySeasonLifecycleConcurrencyIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID CROP_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final Scenario ACTIVITY_FIRST = Scenario.numbered(41, "ACTIVITY-FIRST");
    private static final Scenario SEASON_FIRST = Scenario.numbered(51, "SEASON-FIRST");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
        createScenario(ACTIVITY_FIRST);
        createScenario(SEASON_FIRST);
    }

    @Test
    void activityCreationFirstBlocksThenRejectsSeasonCompletion() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection activity = tenantRuntimeConnection(POSTGRESQL)) {
            insertActivity(activity, ACTIVITY_FIRST);
            CountDownLatch started = new CountDownLatch(1);
            Future<Throwable> completion = executor.submit(() -> completeSeason(ACTIVITY_FIRST, started));

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertBlocked(completion);
            activity.commit();

            assertConstraint(
                    completion.get(10, TimeUnit.SECONDS),
                    "season_transition_requires_closed_activities");
        } finally {
            executor.shutdownNow();
        }
        assertScenario(ACTIVITY_FIRST, "ACTIVE", 1);
    }

    @Test
    void seasonCompletionFirstBlocksThenRejectsActivityCreation() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection completion = tenantRuntimeConnection(POSTGRESQL)) {
            completeSeason(completion, SEASON_FIRST);
            CountDownLatch started = new CountDownLatch(1);
            Future<Throwable> insertion = executor.submit(() -> insertActivity(SEASON_FIRST, started));

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertBlocked(insertion);
            completion.commit();

            assertConstraint(
                    insertion.get(10, TimeUnit.SECONDS),
                    "live_activity_requires_live_season");
        } finally {
            executor.shutdownNow();
        }
        assertScenario(SEASON_FIRST, "COMPLETED", 0);
    }

    private static void createScenario(Scenario scenario) throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            execute(operator, """
                    INSERT INTO seasons (
                        id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                        planned_start_date, planned_end_date, started_on,
                        planted_area_hectares, status)
                    VALUES ('%s', '%s', '%s', '%s', '%s', '%s-SEASON', '%s Season',
                            DATE '2027-01-01', DATE '2027-12-31', DATE '2027-01-01',
                            1.0000, 'ACTIVE')
                    """.formatted(
                    scenario.seasonId(), TENANT_ID, FARM_ID, FIELD_ID, CROP_ID,
                    scenario.code(), scenario.code()));
        }
    }

    private void insertActivity(Connection connection, Scenario scenario) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO activities (
                    id, tenant_id, farm_id, field_id, season_id, activity_type_code,
                    code, title, planned_start_at, due_at, status)
                VALUES (?, ?, ?, ?, ?, 'HARVEST', ?, ?,
                        TIMESTAMPTZ '2027-08-01 01:00:00Z',
                        TIMESTAMPTZ '2027-08-01 03:00:00Z', 'PLANNED')
                """)) {
            statement.setObject(1, scenario.activityId());
            statement.setObject(2, TENANT_ID);
            statement.setObject(3, FARM_ID);
            statement.setObject(4, FIELD_ID);
            statement.setObject(5, scenario.seasonId());
            statement.setString(6, scenario.code());
            statement.setString(7, scenario.code() + " Activity");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void completeSeason(Connection connection, Scenario scenario) throws Exception {
        try (var statement = connection.prepareStatement("""
                UPDATE seasons
                   SET status = 'COMPLETED', ended_on = DATE '2027-08-02',
                       version = version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """)) {
            statement.setObject(1, scenario.seasonId());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private Throwable completeSeason(Scenario scenario, CountDownLatch started) {
        started.countDown();
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            completeSeason(connection, scenario);
            connection.commit();
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private Throwable insertActivity(Scenario scenario, CountDownLatch started) {
        started.countDown();
        try (Connection connection = tenantRuntimeConnection(POSTGRESQL)) {
            insertActivity(connection, scenario);
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

    private void assertScenario(Scenario scenario, String status, long activities) throws Exception {
        try (Connection operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, "SELECT count(*) FROM seasons WHERE id = '"
                    + scenario.seasonId() + "' AND status = '" + status + "'"))
                    .isEqualTo(1);
            assertThat(count(operator, "SELECT count(*) FROM activities WHERE id = '"
                    + scenario.activityId() + "'"))
                    .isEqualTo(activities);
        }
    }

    private record Scenario(UUID seasonId, UUID activityId, String code) {

        private static Scenario numbered(int number, String code) {
            String suffix = String.format("%012d", number);
            return new Scenario(
                    UUID.fromString("52000000-0000-0000-0000-" + suffix),
                    UUID.fromString("53000000-0000-0000-0000-" + suffix),
                    code);
        }
    }
}
