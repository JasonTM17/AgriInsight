package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class WarehouseAssignmentLifecycleDatabaseInvariantIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID ACTOR_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID PROFILE_ID = UUID.fromString("56000000-0000-0000-0000-000000000001");
    private static final UUID WAREHOUSE_ID = UUID.fromString("56000000-0000-0000-0000-000000000002");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("56000000-0000-0000-0000-000000000003");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();

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
    void activeAssignmentRejectsEitherInactiveTarget() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                insertProfile(harness, PROFILE_ID, false);
                insertWarehouse(harness, WAREHOUSE_ID, true);
                return null;
            });

            assertThatThrownBy(() -> harness.withinTenant(() -> {
                insertAssignment(harness, ASSIGNMENT_ID, PROFILE_ID, WAREHOUSE_ID);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("active tenant profile");

            UUID activeProfile = UUID.fromString("56000000-0000-0000-0000-000000000004");
            UUID inactiveWarehouse = UUID.fromString("56000000-0000-0000-0000-000000000005");
            harness.withinTenant(() -> {
                insertProfile(harness, activeProfile, true);
                insertWarehouse(harness, inactiveWarehouse, false);
                return null;
            });
            assertThatThrownBy(() -> harness.withinTenant(() -> {
                insertAssignment(
                        harness,
                        UUID.fromString("56000000-0000-0000-0000-000000000006"),
                        activeProfile,
                        inactiveWarehouse);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("active warehouse");
        }
    }

    @Test
    void parentDeactivationRequiresRevocationFirst() throws Throwable {
        authenticateTenantAdmin();
        UUID profileId = UUID.fromString("56000000-0000-0000-0000-000000000011");
        UUID warehouseId = UUID.fromString("56000000-0000-0000-0000-000000000012");
        UUID assignmentId = UUID.fromString("56000000-0000-0000-0000-000000000013");
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            harness.withinTenant(() -> {
                insertProfile(harness, profileId, true);
                insertWarehouse(harness, warehouseId, true);
                insertAssignment(harness, assignmentId, profileId, warehouseId);
                return null;
            });

            assertThatThrownBy(() -> harness.withinTenant(() -> {
                harness.jdbcTemplate().update(
                        "UPDATE user_profiles SET active = FALSE WHERE tenant_id = ? AND id = ?",
                        TENANT_ID, profileId);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("warehouse assignments to be revoked");
            assertThatThrownBy(() -> harness.withinTenant(() -> {
                harness.jdbcTemplate().update(
                        "UPDATE warehouses SET active = FALSE WHERE tenant_id = ? AND id = ?",
                        TENANT_ID, warehouseId);
                return null;
            })).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("active assignments to be revoked");

            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        UPDATE user_warehouse_assignments
                           SET revoked_at = CURRENT_TIMESTAMP, version = version + 1,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE tenant_id = ? AND id = ?
                        """, TENANT_ID, assignmentId);
                assertThat(harness.jdbcTemplate().update(
                        "UPDATE user_profiles SET active = FALSE WHERE tenant_id = ? AND id = ?",
                        TENANT_ID, profileId)).isOne();
                assertThat(harness.jdbcTemplate().update(
                        "UPDATE warehouses SET active = FALSE WHERE tenant_id = ? AND id = ?",
                        TENANT_ID, warehouseId)).isOne();
                return null;
            });
        }
    }

    private void insertProfile(
            TenantTransactionTestHarness harness, UUID profileId, boolean active) {
        harness.jdbcTemplate().update("""
                INSERT INTO user_profiles (id, tenant_id, display_name, active)
                VALUES (?, ?, 'Warehouse Assignment User', ?)
                """, profileId, TENANT_ID, active);
    }

    private void insertWarehouse(
            TenantTransactionTestHarness harness, UUID warehouseId, boolean active) {
        harness.jdbcTemplate().update("""
                INSERT INTO warehouses (id, tenant_id, code, display_name, active)
                VALUES (?, ?, ?, 'Warehouse Assignment Target', ?)
                """, warehouseId, TENANT_ID, "WH-" + warehouseId.toString().substring(28), active);
    }

    private void insertAssignment(
            TenantTransactionTestHarness harness,
            UUID assignmentId,
            UUID profileId,
            UUID warehouseId) {
        harness.jdbcTemplate().update("""
                INSERT INTO user_warehouse_assignments (
                    id, tenant_id, user_profile_id, warehouse_id)
                VALUES (?, ?, ?, ?)
                """, assignmentId, TENANT_ID, profileId, warehouseId);
    }

    private void authenticateTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return ACTOR_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return ACTOR_ID.toString(); }
    }
}
