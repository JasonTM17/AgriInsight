package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantAuditEventTest {

    private static final ScopeContext SCOPE = new ScopeContext(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            ScopeContext.Type.TENANT,
            Optional.empty());

    @Test
    void normalizesBoundedOptionalMetadata() {
        TenantAuditEvent event = new TenantAuditEvent(
                SCOPE,
                TenantAuditEvent.Action.ROLE_GRANTED,
                TenantAuditEvent.TargetType.USER_ROLE,
                Optional.of(UUID.fromString("22000000-0000-0000-0000-000000000001")),
                Optional.of(" TENANT_ADMIN "),
                Optional.of(" ACCESS_APPROVED "),
                Optional.of(" request-01 "),
                TenantAuditEvent.Outcome.SUCCEEDED);

        assertThat(event.targetReference()).contains("TENANT_ADMIN");
        assertThat(event.reasonCode()).contains("ACCESS_APPROVED");
        assertThat(event.correlationId()).contains("request-01");
    }

    @Test
    void rejectsUnsafeReasonAndCorrelationValues() {
        assertThatThrownBy(() -> event("reason with spaces", "request-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
        assertThatThrownBy(() -> event("ACCESS_APPROVED", "../../secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }

    private TenantAuditEvent event(String reasonCode, String correlationId) {
        return new TenantAuditEvent(
                SCOPE,
                TenantAuditEvent.Action.USER_DEACTIVATED,
                TenantAuditEvent.TargetType.USER_PROFILE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(reasonCode),
                Optional.of(correlationId),
                TenantAuditEvent.Outcome.SUCCEEDED);
    }
}
