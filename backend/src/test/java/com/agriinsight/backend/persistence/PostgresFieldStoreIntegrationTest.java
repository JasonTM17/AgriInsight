package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.FieldCommands;
import com.agriinsight.backend.farm.application.FieldQuery;
import com.agriinsight.backend.farm.domain.Field;
import com.agriinsight.backend.farm.infrastructure.PostgresFieldStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class PostgresFieldStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("41000000-0000-0000-0000-000000000004");
    private static final UUID EXISTING_FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID UNASSIGNED_FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000071");
    private static final UUID UNASSIGNED_FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000072");
    private static final UUID NEW_FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000073");
    private static final UUID TENANT_FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000074");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("FIELD_CHANGE"), Optional.of("request-store-2"));

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
    void assignmentScopePrecedesPagingAndVersionedLifecycle() throws Throwable {
        authenticateFarmManager();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresFieldStore store = new PostgresFieldStore(harness.jdbcTemplate());
            ScopeContext listScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
            ScopeContext farmScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));

            harness.withinTenant(() -> {
                insertUnassignedField(harness);
                var page = store.findAll(listScope, query(1));
                assertThat(page.items()).extracting(item -> item.id())
                        .containsExactly(EXISTING_FIELD_ID);
                assertThat(page.hasMore()).isFalse();
                assertThat(store.findById(listScope, UNASSIGNED_FIELD_ID)).isEmpty();
                assertThat(store.farmVisible(listScope, FARM_ID)).isTrue();
                assertThat(store.farmVisible(listScope, UNASSIGNED_FARM_ID)).isFalse();
                ScopeContext unassignedScope = ScopeContext.domain(
                        PRINCIPAL, ScopeContext.Type.FARM, Optional.of(UNASSIGNED_FARM_ID));
                assertThat(store.create(
                        unassignedScope,
                        new Field(
                                UUID.fromString("41000000-0000-0000-0000-000000000075"),
                                TENANT_ID, UNASSIGNED_FARM_ID, "FIELD-HIDDEN", "Hidden Field",
                                new BigDecimal("1"), Optional.empty(), Optional.empty(),
                                Optional.empty(), Optional.empty())))
                        .isEmpty();
                assertThat(store.liveParentsAvailable(
                        farmScope, FARM_ID, Optional.of(EMPLOYEE_ID))).isTrue();

                var created = store.create(farmScope, field()).orElseThrow();
                assertThat(created.coordinates()).isPresent();
                assertThat(created.active()).isTrue();

                var update = new FieldCommands.Update(
                        Optional.empty(), Optional.of("Updated Field"),
                        Optional.of(new BigDecimal("7.25")), Optional.of(Optional.empty()),
                        Optional.of(Optional.empty()), Optional.of(Optional.empty()),
                        Optional.of(Optional.of("Sprinkler")), 0, AUDIT);
                var updated = store.update(farmScope, NEW_FIELD_ID, 0, update).orElseThrow();
                assertThat(updated.displayName()).isEqualTo("Updated Field");
                assertThat(updated.responsibleEmployeeId()).isEmpty();
                assertThat(updated.coordinates()).isEmpty();
                assertThat(updated.version()).isEqualTo(1);
                assertThat(store.update(farmScope, NEW_FIELD_ID, 0, update)).isEmpty();

                var inactive = store.updateActive(farmScope, NEW_FIELD_ID, 1, false).orElseThrow();
                assertThat(inactive.active()).isFalse();
                var active = store.updateActive(farmScope, NEW_FIELD_ID, 2, true).orElseThrow();
                assertThat(active.active()).isTrue();
                assertThat(store.hasDeactivationBlockers(farmScope, EXISTING_FIELD_ID)).isTrue();
                assertThat(store.updateActive(farmScope, EXISTING_FIELD_ID, 0, false)).isEmpty();

                ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);
                var tenantCreated = store.create(
                        tenantScope, field(TENANT_FIELD_ID, "FIELD-TENANT")).orElseThrow();
                var tenantUpdate = new FieldCommands.Update(
                        Optional.empty(), Optional.of("Tenant Managed Field"),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), 0, AUDIT);
                var tenantUpdated = store.update(
                        tenantScope, tenantCreated.id(), 0, tenantUpdate).orElseThrow();
                var tenantInactive = store.updateActive(
                        tenantScope, tenantUpdated.id(), 1, false).orElseThrow();
                assertThat(tenantUpdated.displayName()).isEqualTo("Tenant Managed Field");
                assertThat(tenantInactive.active()).isFalse();
                assertThat(tenantInactive.version()).isEqualTo(2);
                return null;
            });
        }
    }

    private void insertUnassignedField(TenantTransactionTestHarness harness) {
        harness.jdbcTemplate().update("""
                INSERT INTO farms (id, tenant_id, code, display_name)
                VALUES (?, ?, 'UNASSIGNED-FIELD-FARM', 'Unassigned Field Farm')
                """, UNASSIGNED_FARM_ID, TENANT_ID);
        harness.jdbcTemplate().update("""
                INSERT INTO fields (id, tenant_id, farm_id, code, display_name, area_hectares)
                VALUES (?, ?, ?, 'UNASSIGNED-FIELD', 'Unassigned Field', 5.0000)
                """, UNASSIGNED_FIELD_ID, TENANT_ID, UNASSIGNED_FARM_ID);
    }

    private Field field() {
        return field(NEW_FIELD_ID, "FIELD-NEW");
    }

    private Field field(UUID fieldId, String code) {
        return new Field(
                fieldId, TENANT_ID, FARM_ID, code, "New Field",
                new BigDecimal("8.5"), Optional.of(EMPLOYEE_ID),
                Optional.of(new Field.Coordinates(
                        new BigDecimal("10.1234"), new BigDecimal("106.5678"))),
                Optional.of("Loam"), Optional.of("Drip"));
    }

    private FieldQuery query(int limit) {
        return new FieldQuery(
                limit, 0, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private void authenticateFarmManager() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(Role.FARM_MANAGER.authority()))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
