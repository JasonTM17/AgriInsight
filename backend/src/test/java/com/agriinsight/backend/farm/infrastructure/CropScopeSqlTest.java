package com.agriinsight.backend.farm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CropScopeSqlTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();

    @Test
    void farmListScopeRequiresAnyUnrevokedAssignment() {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM crops AS crop WHERE true");
        List<Object> parameters = new ArrayList<>();

        CropScopeSql.append(sql, parameters, ScopeContext.domain(
                PRINCIPAL, ScopeContext.Type.FARM, Optional.empty()));

        assertThat(sql).contains("user_farm_assignments", "assignment.revoked_at IS NULL");
        assertThat(parameters).containsExactly(PROFILE_ID);
    }

    @Test
    void resourceFarmScopeNarrowsTheAssignmentWithoutNarrowingTheSharedCatalog() {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM crops AS crop WHERE true");
        List<Object> parameters = new ArrayList<>();

        CropScopeSql.append(sql, parameters, ScopeContext.domain(
                PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID)));

        assertThat(sql).contains("assignment.farm_id = ?").doesNotContain("crop.farm_id");
        assertThat(parameters).containsExactly(PROFILE_ID, FARM_ID);
    }

    @Test
    void tenantScopeDoesNotRequireFarmAssignment() {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM crops AS crop WHERE true");
        List<Object> parameters = new ArrayList<>();

        CropScopeSql.append(sql, parameters, ScopeContext.tenant(PRINCIPAL));

        assertThat(sql.toString()).doesNotContain("user_farm_assignments");
        assertThat(parameters).isEmpty();
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
