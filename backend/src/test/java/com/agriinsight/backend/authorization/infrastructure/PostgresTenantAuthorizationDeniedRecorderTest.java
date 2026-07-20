package com.agriinsight.backend.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.application.TenantAuditPublisher;
import com.agriinsight.backend.shared.application.TenantAuthorizationDeniedRecorder;
import com.agriinsight.backend.shared.persistence.TenantContextBinder;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PostgresTenantAuthorizationDeniedRecorderTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void bindsTenantAndCommitsDenialAuditInAnIndependentTransaction() {
        TenantContextBinder contextBinder = mock(TenantContextBinder.class);
        TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        PostgresTenantAuthorizationDeniedRecorder recorder =
                new PostgresTenantAuthorizationDeniedRecorder(contextBinder, auditPublisher, transactionManager);

        recorder.record(new TenantAuthorizationDeniedRecorder.Decision(
                TENANT_ID,
                PROFILE_ID,
                "/api/v1/users",
                "ROUTE_PERMISSION_DENIED",
                Optional.empty(),
                Optional.of("deny-01")));

        var event = ArgumentCaptor.forClass(TenantAuditEvent.class);
        var order = inOrder(contextBinder, auditPublisher, transactionManager);
        order.verify(contextBinder).bind(TENANT_ID);
        order.verify(auditPublisher).publish(event.capture());
        order.verify(transactionManager).commit(status);
        assertThat(event.getValue().action()).isEqualTo(TenantAuditEvent.Action.AUTHORIZATION_DENIED);
        assertThat(event.getValue().outcome()).isEqualTo(TenantAuditEvent.Outcome.DENIED);
        assertThat(event.getValue().targetReference()).contains("/api/v1/users");
        assertThat(event.getValue().correlationId()).contains("deny-01");
    }

    @Test
    void propagatesPersistenceFailureSoTheResponseBoundaryCanPreserveAndSignalTheDenial() {
        TenantContextBinder contextBinder = mock(TenantContextBinder.class);
        TenantAuditPublisher auditPublisher = mock(TenantAuditPublisher.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        doThrow(new IllegalStateException("audit unavailable")).when(auditPublisher).publish(any());
        PostgresTenantAuthorizationDeniedRecorder recorder =
                new PostgresTenantAuthorizationDeniedRecorder(contextBinder, auditPublisher, transactionManager);
        var decision = new TenantAuthorizationDeniedRecorder.Decision(
                TENANT_ID,
                PROFILE_ID,
                "/api/v1/users",
                "ROUTE_PERMISSION_DENIED",
                Optional.empty(),
                Optional.of("deny-02"));

        assertThatThrownBy(() -> recorder.record(decision))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        verify(transactionManager).rollback(status);
    }
}
