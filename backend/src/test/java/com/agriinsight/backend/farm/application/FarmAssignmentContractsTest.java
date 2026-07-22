package com.agriinsight.backend.farm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FarmAssignmentContractsTest {

    private static final UUID ID = UUID.fromString("38000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FARM_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(Optional.empty(), Optional.of("farm-assignment-1"));

    @Test
    void commandVersionsCannotBeNegative() {
        assertThatThrownBy(() -> new FarmAssignmentCommands.Grant(
                PROFILE_ID, FARM_ID, -1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FarmAssignmentCommands.Revoke(-1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revokedTimestampDefinesOneWayActiveProjection() {
        var active = new FarmAssignmentRecord(
                ID, TENANT_ID, PROFILE_ID, FARM_ID, Optional.empty(), 0);
        var revoked = new FarmAssignmentRecord(
                ID, TENANT_ID, PROFILE_ID, FARM_ID, Optional.of(Instant.EPOCH), 1);

        assertThat(active.active()).isTrue();
        assertThat(revoked.active()).isFalse();
        assertThatThrownBy(() -> new FarmAssignmentRecord(
                ID, TENANT_ID, PROFILE_ID, FARM_ID, Optional.empty(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
