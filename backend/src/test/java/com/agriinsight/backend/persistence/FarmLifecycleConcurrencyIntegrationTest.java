package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FarmRecord;
import com.agriinsight.backend.farm.domain.Farm;
import com.agriinsight.backend.farm.infrastructure.PostgresFarmStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmLifecycleConcurrencyIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000031");
    private static final UUID FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000032");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);

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
    void concurrentLiveChildInsertSerializesBeforeFarmDeactivation() throws Throwable {
        try (TenantTransactionTestHarness setup = runtimeHarness()) {
            authenticate();
            setup.withinTenant(() -> {
                new PostgresFarmStore(setup.jdbcTemplate()).create(
                        TENANT_SCOPE,
                        new Farm(FARM_ID, TENANT_ID, "RACE-FARM", "Race Farm"));
                return null;
            });
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection child = tenantRuntimeConnection(POSTGRESQL);
                TenantTransactionTestHarness lifecycle = runtimeHarness()) {
            insertUncommittedField(child);
            CountDownLatch started = new CountDownLatch(1);
            Future<Optional<FarmRecord>> deactivation = executor.submit(() ->
                    deactivate(lifecycle, started));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deactivation.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            child.commit();
            assertThat(deactivation.get(10, TimeUnit.SECONDS)).isEmpty();
            authenticate();
            assertThat(lifecycle.withinTenant(() -> new PostgresFarmStore(lifecycle.jdbcTemplate())
                    .findById(TENANT_SCOPE, FARM_ID)
                    .orElseThrow()
                    .active())).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<FarmRecord> deactivate(
            TenantTransactionTestHarness harness,
            CountDownLatch started) {
        authenticate();
        started.countDown();
        try {
            return harness.withinTenant(() -> new PostgresFarmStore(harness.jdbcTemplate())
                    .updateActive(TENANT_SCOPE, FARM_ID, 0, false));
        } catch (Throwable exception) {
            throw new IllegalStateException("Concurrent deactivation failed", exception);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void insertUncommittedField(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                INSERT INTO fields (
                    id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES (?, ?, ?, 'RACE-FIELD', 'Race Field', 1.0000)
                """)) {
            statement.setObject(1, FIELD_ID);
            statement.setObject(2, TENANT_ID);
            statement.setObject(3, FARM_ID);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private TenantTransactionTestHarness runtimeHarness() {
        return TenantTransactionTestHarness.runtime(POSTGRESQL, "agriinsight");
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {

        @Override
        public UUID profileId() {
            return PROFILE_ID;
        }

        @Override
        public UUID tenantId() {
            return TENANT_ID;
        }

        @Override
        public String getName() {
            return PROFILE_ID.toString();
        }
    }
}
