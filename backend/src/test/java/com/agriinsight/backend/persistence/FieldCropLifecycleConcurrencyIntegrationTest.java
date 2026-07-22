package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.FARM_SCOPE;
import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.TENANT_SCOPE;
import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.assertState;
import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.authenticate;
import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.createParents;
import static com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.insertLiveSeason;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.farm.infrastructure.PostgresCropStore;
import com.agriinsight.backend.farm.infrastructure.PostgresFieldStore;
import com.agriinsight.backend.persistence.FieldCropLifecycleConcurrencyTestSupport.Scenario;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FieldCropLifecycleConcurrencyIntegrationTest {

    private static final Scenario CHILD_FIRST = Scenario.numbered(81, "RACE-CHILD");
    private static final Scenario FIELD_FIRST = Scenario.numbered(91, "RACE-FIELD");
    private static final Scenario CROP_FIRST = Scenario.numbered(101, "RACE-CROP");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void liveSeasonFirstBlocksThenPreventsBothApplicationDeactivations() throws Exception {
        createParents(POSTGRESQL, CHILD_FIRST);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(2);
        try (Connection season = tenantRuntimeConnection(POSTGRESQL);
                TenantTransactionTestHarness field = runtimeHarness();
                TenantTransactionTestHarness crop = runtimeHarness()) {
            insertLiveSeason(season, CHILD_FIRST);
            Future<Boolean> fieldChanged = executor.submit(() ->
                    deactivateField(field, CHILD_FIRST, started));
            Future<Boolean> cropChanged = executor.submit(() ->
                    deactivateCrop(crop, CHILD_FIRST, started));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertBlocked(fieldChanged);
            assertBlocked(cropChanged);

            season.commit();
            assertThat(fieldChanged.get(10, TimeUnit.SECONDS)).isFalse();
            assertThat(cropChanged.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }
        assertState(POSTGRESQL, CHILD_FIRST, true, true, 1);
    }

    @Test
    void fieldDeactivationFirstBlocksThenRejectsLiveSeason() throws Throwable {
        parentFirstRejectsSeason(FIELD_FIRST, Parent.FIELD, "live_season_requires_active_field");
        assertState(POSTGRESQL, FIELD_FIRST, false, true, 0);
    }

    @Test
    void cropDeactivationFirstBlocksThenRejectsLiveSeason() throws Throwable {
        parentFirstRejectsSeason(CROP_FIRST, Parent.CROP, "live_season_requires_active_crop");
        assertState(POSTGRESQL, CROP_FIRST, true, false, 0);
    }

    private void parentFirstRejectsSeason(
            Scenario scenario,
            Parent parent,
            String expectedConstraint) throws Throwable {
        createParents(POSTGRESQL, scenario);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection season = tenantRuntimeConnection(POSTGRESQL);
                TenantTransactionTestHarness lifecycle = runtimeHarness()) {
            authenticate();
            Future<Void> insertion = lifecycle.withinTenant(() -> {
                assertThat(deactivate(parent, lifecycle, scenario)).isTrue();
                Future<Void> operation = executor.submit(() -> {
                    insertLiveSeason(season, scenario);
                    season.commit();
                    return null;
                });
                assertBlocked(operation);
                return operation;
            });
            assertConstraint(insertion, expectedConstraint);
            season.rollback();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean deactivate(
            Parent parent,
            TenantTransactionTestHarness harness,
            Scenario scenario) {
        return switch (parent) {
            case FIELD -> new PostgresFieldStore(harness.jdbcTemplate())
                    .updateActive(FARM_SCOPE, scenario.fieldId(), 0, false).isPresent();
            case CROP -> new PostgresCropStore(harness.jdbcTemplate())
                    .updateActive(TENANT_SCOPE, scenario.cropId(), 0, false).isPresent();
        };
    }

    private boolean deactivateField(
            TenantTransactionTestHarness harness,
            Scenario scenario,
            CountDownLatch started) {
        return withinTenant(harness, started, () -> new PostgresFieldStore(harness.jdbcTemplate())
                .updateActive(FARM_SCOPE, scenario.fieldId(), 0, false).isPresent());
    }

    private boolean deactivateCrop(
            TenantTransactionTestHarness harness,
            Scenario scenario,
            CountDownLatch started) {
        return withinTenant(harness, started, () -> new PostgresCropStore(harness.jdbcTemplate())
                .updateActive(TENANT_SCOPE, scenario.cropId(), 0, false).isPresent());
    }

    private boolean withinTenant(
            TenantTransactionTestHarness harness,
            CountDownLatch started,
            TenantTransactionTestHarness.ThrowingSupplier<Boolean> operation) {
        authenticate();
        try {
            return harness.withinTenant(() -> {
                started.countDown();
                return operation.get();
            });
        } catch (Throwable exception) {
            throw new IllegalStateException("Concurrent lifecycle change failed", exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertBlocked(Future<?> operation) {
        assertThatThrownBy(() -> operation.get(500, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private void assertConstraint(Future<Void> operation, String expectedConstraint) {
        assertThatThrownBy(() -> operation.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .isInstanceOfSatisfying(PSQLException.class, sqlException ->
                                assertThat(sqlException.getServerErrorMessage().getConstraint())
                                        .isEqualTo(expectedConstraint)));
    }

    private TenantTransactionTestHarness runtimeHarness() {
        return TenantTransactionTestHarness.runtime(POSTGRESQL, "agriinsight");
    }

    private enum Parent { FIELD, CROP }
}
