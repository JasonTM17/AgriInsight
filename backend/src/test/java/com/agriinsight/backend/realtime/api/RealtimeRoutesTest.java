package com.agriinsight.backend.realtime.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.shared.api.SecuredRouteRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RealtimeRoutesTest {

    @Test
    void publishesTheExactRealtimeSummaryAndOperationalAlertContracts() {
        SecuredRouteRegistry registry = new SecuredRouteRegistry(List.of(new RealtimeRoutes()));

        assertThat(registry.routes()).containsExactly(
                new SecuredRouteRegistry.Route(
                        HttpMethod.GET,
                        "/api/v1/realtime/alerts",
                        Optional.of(Permission.REALTIME_ALERT_READ.name())),
                new SecuredRouteRegistry.Route(
                        HttpMethod.GET,
                        "/api/v1/realtime/summary",
                        Optional.of(Permission.REALTIME_READ.name())),
                new SecuredRouteRegistry.Route(
                        HttpMethod.POST,
                        "/api/v1/realtime/alerts/{id}/acknowledgements",
                        Optional.of(Permission.REALTIME_ALERT_ACKNOWLEDGE.name())));
    }
}
