package com.agriinsight.backend.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseAssignmentContractsTest {

    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.empty());

    @Test
    void activeStateDerivesOnlyFromRevocationTimestamp() {
        UUID tenantId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        var active = new WarehouseAssignmentRecord(
                UUID.randomUUID(), tenantId, profileId, warehouseId, Optional.empty(), 0);
        var revoked = new WarehouseAssignmentRecord(
                UUID.randomUUID(), tenantId, profileId, warehouseId,
                Optional.of(Instant.now()), 1);

        assertThat(active.active()).isTrue();
        assertThat(revoked.active()).isFalse();
    }

    @Test
    void commandVersionsMustBeNonnegative() {
        assertThatThrownBy(() -> new WarehouseAssignmentCommands.Grant(
                UUID.randomUUID(), UUID.randomUUID(), -1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> new WarehouseAssignmentCommands.Revoke(-1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }
}
