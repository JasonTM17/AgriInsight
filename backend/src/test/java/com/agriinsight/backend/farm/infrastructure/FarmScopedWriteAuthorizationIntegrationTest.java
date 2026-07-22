package com.agriinsight.backend.farm.infrastructure;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.tenantRuntimeConnection;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.authorization.domain.Role;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class FarmScopedWriteAuthorizationIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000081");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("41000000-0000-0000-0000-000000000082");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Throwable {
        migrateAndSeed(POSTGRESQL);
        authenticate();
        try (TenantTransactionTestHarness harness = runtimeHarness()) {
            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO farms (id, tenant_id, code, display_name)
                        VALUES (?, ?, 'LOCK-FARM', 'Lock Farm')
                        """, FARM_ID, TENANT_ID);
                harness.jdbcTemplate().update("""
                        INSERT INTO user_farm_assignments (id, tenant_id, user_profile_id, farm_id)
                        VALUES (?, ?, ?, ?)
                        """, ASSIGNMENT_ID, TENANT_ID, PROFILE_ID, FARM_ID);
                return null;
            });
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revocationCannotPassScopedWriteAuthorizationMidTransaction() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection revoker = tenantRuntimeConnection(POSTGRESQL)) {
            revokeWithoutCommit(revoker);
            CountDownLatch started = new CountDownLatch(1);
            Future<Boolean> authorization = executor.submit(() -> {
                started.countDown();
                authenticate();
                try (TenantTransactionTestHarness harness = runtimeHarness()) {
                    return harness.withinTenant(() -> FarmScopeSql.lockWriteAuthorization(
                            harness.jdbcTemplate(),
                            ScopeContext.domain(
                                    PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID)),
                            FARM_ID));
                } catch (Throwable exception) {
                    throw new IllegalStateException("Scoped write authorization failed", exception);
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> authorization.get(500, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            revoker.commit();
            assertThat(authorization.get(10, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }
    }

    private void revokeWithoutCommit(Connection connection) throws Exception {
        try (var statement = connection.prepareStatement("""
                UPDATE user_farm_assignments
                   SET revoked_at = CURRENT_TIMESTAMP,
                       version = version + 1,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """)) {
            statement.setObject(1, ASSIGNMENT_ID);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static TenantTransactionTestHarness runtimeHarness() {
        return TenantTransactionTestHarness.runtime(POSTGRESQL, "agriinsight");
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
