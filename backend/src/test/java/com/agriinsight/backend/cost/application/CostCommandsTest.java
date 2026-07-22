package com.agriinsight.backend.cost.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.cost.domain.CostCategory;
import com.agriinsight.backend.cost.domain.CostTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CostCommandsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("COST_CHANGE"), Optional.of("request-cost-01"));

    @Test
    void correctionReasonIsRequiredAndNormalized() {
        assertThatThrownBy(() -> correction(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("correctionReason is required");
        assertThat(correction("  Correct invoice allocation  ").correctionReason())
                .isEqualTo("Correct invoice allocation");
        assertThatThrownBy(() -> correction("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correctionReason must not be blank");
    }

    private CostCommands.Correct correction(String reason) {
        return new CostCommands.Correct(
                CostTarget.tenant(),
                CostCategory.OTHER,
                new BigDecimal("100000"),
                Instant.parse("2027-09-01T00:00:00Z"),
                Optional.empty(),
                Optional.empty(),
                reason,
                AUDIT);
    }
}
