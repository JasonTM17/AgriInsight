package com.agriinsight.backend.farm.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.farm.domain.Field;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

final class FieldApplicationTestFixtures {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID FARM_ID = UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID EMPLOYEE_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext LIST_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.empty());
    static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
    static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("FIELD_CHANGE"), Optional.of("request-05"));

    private FieldApplicationTestFixtures() {
    }

    static FieldRecord field(long version, boolean active) {
        return new FieldRecord(
                FIELD_ID, TENANT_ID, FARM_ID, "FIELD-A", "North Field",
                new BigDecimal("12.5"), Optional.of(EMPLOYEE_ID),
                Optional.of(new Field.Coordinates(
                        new BigDecimal("10.1"), new BigDecimal("106.2"))),
                Optional.of("Loam"), Optional.of("Drip"), active, version);
    }

    static FieldCommands.Create createCommand() {
        return new FieldCommands.Create(
                FARM_ID, "FIELD-A", "North Field", new BigDecimal("12.5"),
                Optional.of(EMPLOYEE_ID), Optional.empty(), Optional.of("Loam"),
                Optional.of("Drip"), AUDIT);
    }

    static FieldCommands.Update updateCommand(long version) {
        return new FieldCommands.Update(
                Optional.empty(), Optional.of("Updated Field"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                version, AUDIT);
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
