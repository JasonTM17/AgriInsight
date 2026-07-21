package com.agriinsight.backend.farm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FarmScopeSqlTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000002");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();

    @Test
    void resourceScopeConstrainsTheFarmBeforeAssignmentLookup() {
        ScopeContext scope = ScopeContext.domain(
                PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
        StringBuilder sql = new StringBuilder("SELECT 1 FROM farms AS farm WHERE true");
        List<Object> parameters = new ArrayList<>();

        FarmScopeSql.append(sql, parameters, scope, null);

        assertThat(sql).contains("farm.id = ?", "assignment.farm_id = farm.id");
        assertThat(parameters).containsExactly(FARM_ID, PROFILE_ID);
    }

    @Test
    void resourceScopeRejectsAnExplicitDifferentFarm() {
        ScopeContext scope = ScopeContext.domain(
                PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));

        assertThatThrownBy(() -> FarmScopeSql.append(
                new StringBuilder(), new ArrayList<>(), scope, OTHER_FARM_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another farm");
    }

    @Test
    void listScopeStillRequiresAnActiveAssignmentWithoutOneFarmConstraint() {
        ScopeContext scope = ScopeContext.domain(
                PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
        StringBuilder sql = new StringBuilder("SELECT 1 FROM farms AS farm WHERE true");
        List<Object> parameters = new ArrayList<>();

        FarmScopeSql.append(sql, parameters, scope, null);

        assertThat(sql).doesNotContain("farm.id = ?").contains("assignment.revoked_at IS NULL");
        assertThat(parameters).containsExactly(PROFILE_ID);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
