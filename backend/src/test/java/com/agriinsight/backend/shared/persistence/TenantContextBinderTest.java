package com.agriinsight.backend.shared.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantContextBinderTest {

    @Test
    void rejectsBindingOutsideAnActiveTransaction() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenantContextBinder binder = new TenantContextBinder(jdbcTemplate);

        assertThatThrownBy(() -> binder.bind(UUID.randomUUID()))
                .isInstanceOf(TenantContextRequiredException.class)
                .hasMessage("Tenant context requires an active transaction");
        verifyNoInteractions(jdbcTemplate);
    }
}
