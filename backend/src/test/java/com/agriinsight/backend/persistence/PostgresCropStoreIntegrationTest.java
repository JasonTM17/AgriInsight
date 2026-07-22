package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.application.CropCommands;
import com.agriinsight.backend.farm.application.CropQuery;
import com.agriinsight.backend.farm.domain.Crop;
import com.agriinsight.backend.farm.infrastructure.PostgresCropStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
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
class PostgresCropStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID UNASSIGNED_PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000075");
    private static final UUID EXISTING_CROP_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID NEW_CROP_ID = UUID.fromString("41000000-0000-0000-0000-000000000076");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal(PROFILE_ID);
    private static final TenantPrincipal UNASSIGNED_PRINCIPAL = new TestPrincipal(UNASSIGNED_PROFILE_ID);
    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("CROP_CHANGE"), Optional.of("request-store-3"));

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
    void assignmentGuardsReadsWhileTenantScopeOwnsVersionedLifecycle() throws Throwable {
        authenticateTenantAdmin();
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresCropStore store = new PostgresCropStore(harness.jdbcTemplate());
            ScopeContext farmScope = ScopeContext.domain(
                    PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
            ScopeContext unassignedScope = ScopeContext.domain(
                    UNASSIGNED_PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
            ScopeContext tenantScope = ScopeContext.tenant(PRINCIPAL);

            harness.withinTenant(() -> {
                var page = store.findAll(farmScope, query(1));
                assertThat(page.items()).extracting(item -> item.id())
                        .containsExactly(EXISTING_CROP_ID);
                assertThat(page.hasMore()).isFalse();
                assertThat(store.findAll(unassignedScope, query(25)).items()).isEmpty();
                assertThat(store.findById(unassignedScope, EXISTING_CROP_ID)).isEmpty();

                var created = store.create(tenantScope, crop());
                assertThat(created.scientificName()).contains("Oryza sativa");
                assertThat(created.active()).isTrue();

                var update = new CropCommands.Update(
                        Optional.empty(), Optional.of("Updated Rice"),
                        Optional.of(Optional.empty()), 0, AUDIT);
                var updated = store.update(tenantScope, NEW_CROP_ID, 0, update).orElseThrow();
                assertThat(updated.displayName()).isEqualTo("Updated Rice");
                assertThat(updated.scientificName()).isEmpty();
                assertThat(updated.version()).isEqualTo(1);
                assertThat(store.update(tenantScope, NEW_CROP_ID, 0, update)).isEmpty();

                var inactive = store.updateActive(tenantScope, NEW_CROP_ID, 1, false).orElseThrow();
                assertThat(inactive.active()).isFalse();
                var active = store.updateActive(tenantScope, NEW_CROP_ID, 2, true).orElseThrow();
                assertThat(active.active()).isTrue();
                assertThat(store.hasDeactivationBlockers(tenantScope, EXISTING_CROP_ID)).isTrue();
                assertThat(store.updateActive(tenantScope, EXISTING_CROP_ID, 0, false)).isEmpty();
                return null;
            });
        }
    }

    private Crop crop() {
        return new Crop(
                NEW_CROP_ID, TENANT_ID, "RICE-A", "Rice A", Optional.of("Oryza sativa"));
    }

    private CropQuery query(int limit) {
        return new CropQuery(limit, 0, Optional.empty(), Optional.empty());
    }

    private void authenticateTenantAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    private record TestPrincipal(UUID profileId) implements TenantPrincipal {
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return profileId.toString(); }
    }
}
