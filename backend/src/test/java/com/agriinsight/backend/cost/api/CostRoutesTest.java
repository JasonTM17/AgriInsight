package com.agriinsight.backend.cost.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class CostRoutesTest {

    @Test
    void publishesExactReadAndManagementContracts() {
        SecuredRouteRegistry registry = new SecuredRouteRegistry(List.of(new CostRoutes()));

        assertThat(registry.routes())
                .extracting(route -> route.method() + " " + route.pattern()
                        + " " + route.requiredAuthority().orElseThrow())
                .containsExactly(
                        "GET /api/v1/cost-entries COST_READ",
                        "GET /api/v1/cost-entries/{id} COST_READ",
                        "GET /api/v1/cost-summaries COST_READ",
                        "POST /api/v1/cost-entries COST_MANAGE",
                        "POST /api/v1/cost-entries/{id}/corrections COST_MANAGE");
        assertThat(registry.contains(HttpMethod.DELETE, "/api/v1/cost-entries/{id}"))
                .isFalse();
        assertThat(Permission.COST_READ.authority()).isEqualTo("COST_READ");
        assertThat(Permission.COST_MANAGE.authority()).isEqualTo("COST_MANAGE");
    }
}
