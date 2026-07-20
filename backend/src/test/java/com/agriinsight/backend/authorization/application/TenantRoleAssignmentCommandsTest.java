package com.agriinsight.backend.authorization.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.domain.Role;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantRoleAssignmentCommandsTest {

    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    @Test
    void rejectsNegativeOptimisticVersionsBeforePersistence() {
        assertThatThrownBy(() -> new TenantRoleAssignmentCommands.Grant(
                Role.DATA_ANALYST,
                -1,
                AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> new TenantRoleAssignmentCommands.Revoke(-1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }
}
