package com.agriinsight.backend.authorization.infrastructure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.application.IdempotencyConflictPublisher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantIdempotencyConflictAuditPublisherTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PRINCIPAL_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COMMAND_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void mapsConflictWithoutRawKeyOrCommandPayload() {
        TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
        TenantIdempotencyConflictAuditPublisher publisher =
                new TenantIdempotencyConflictAuditPublisher(auditPublisher);

        publisher.publish(new IdempotencyConflictPublisher.Conflict(
                TENANT_ID,
                PRINCIPAL_ID,
                COMMAND_ID,
                "/api/v1/users/{id}",
                Optional.of("correlation-1")));

        verify(auditPublisher).publish(new TenantAuditEvent(
                new ScopeContext(TENANT_ID, PRINCIPAL_ID, ScopeContext.Type.TENANT, Optional.empty()),
                TenantAuditEvent.Action.IDEMPOTENCY_CONFLICT,
                TenantAuditEvent.TargetType.API_COMMAND,
                Optional.of(COMMAND_ID),
                Optional.of("/api/v1/users/{id}"),
                Optional.of("IDEMPOTENCY_KEY_REUSED"),
                Optional.of("correlation-1"),
                TenantAuditEvent.Outcome.CONFLICT));
    }
}
