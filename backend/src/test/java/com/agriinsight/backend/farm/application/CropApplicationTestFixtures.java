package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;

final class CropApplicationTestFixtures {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID CROP_ID = UUID.fromString("34000000-0000-0000-0000-000000000001");
    static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
    static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("CROP_CHANGE"), Optional.of("request-06"));

    private CropApplicationTestFixtures() {
    }

    static CropRecord crop(long version, boolean active) {
        return new CropRecord(
                CROP_ID, TENANT_ID, "COFFEE-A", "Arabica Coffee",
                Optional.of("Coffea arabica"), active, version);
    }

    static CropCommands.Create createCommand() {
        return new CropCommands.Create(
                "COFFEE-A", "Arabica Coffee", Optional.of("Coffea arabica"), AUDIT);
    }

    static CropCommands.Update updateCommand(long version) {
        return new CropCommands.Update(
                Optional.empty(), Optional.of("Updated Crop"), Optional.empty(), version, AUDIT);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
