package com.agriinsight.backend.authorization.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.application.TenantAuditEvent;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresTenantAuditPublisherTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void persistsOnlyTheBoundedTenantAuditProjection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        PostgresTenantAuditPublisher publisher = new PostgresTenantAuditPublisher(jdbcTemplate);

        publisher.publish(event());

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), parameters.capture());
        org.assertj.core.api.Assertions.assertThat(parameters.getValue())
                .hasSize(10)
                .containsSequence(
                        TENANT_ID,
                        ACTOR_ID,
                        "USER_DEACTIVATED",
                        "USER_PROFILE",
                        TARGET_ID,
                        null,
                        "ACCESS_REVOKED",
                        "request-01",
                        "SUCCEEDED");
    }

    @Test
    void failsIfTheAuditInsertDoesNotWriteExactlyOneRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        assertThatThrownBy(() -> new PostgresTenantAuditPublisher(jdbcTemplate).publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not persisted");
    }

    private TenantAuditEvent event() {
        ScopeContext scope = new ScopeContext(
                TENANT_ID,
                ACTOR_ID,
                ScopeContext.Type.TENANT,
                Optional.empty());
        return new TenantAuditEvent(
                scope,
                TenantAuditEvent.Action.USER_DEACTIVATED,
                TenantAuditEvent.TargetType.USER_PROFILE,
                Optional.of(TARGET_ID),
                Optional.empty(),
                Optional.of("ACCESS_REVOKED"),
                Optional.of("request-01"),
                TenantAuditEvent.Outcome.SUCCEEDED);
    }
}
