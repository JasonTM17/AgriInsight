package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;

final class EmployeeApplicationTestFixtures {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID EMPLOYEE_ID = UUID.fromString("36000000-0000-0000-0000-000000000001");
    static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);
    static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
    static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("EMPLOYEE_CHANGE"), Optional.of("request-01"));

    private EmployeeApplicationTestFixtures() {
    }

    static EmployeeRecord employee(long version, boolean active) {
        return new EmployeeRecord(
                EMPLOYEE_ID, TENANT_ID, "WORKER-A", "Worker A",
                Optional.of("Technician"), active, version);
    }

    static EmployeeCommands.Create createCommand() {
        return new EmployeeCommands.Create(
                "WORKER-A", "Worker A", Optional.of("Technician"), AUDIT);
    }

    static EmployeeCommands.Update updateCommand(long version) {
        return new EmployeeCommands.Update(
                Optional.empty(), Optional.of("Updated Worker"), Optional.empty(), version, AUDIT);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
