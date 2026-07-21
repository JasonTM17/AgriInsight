package com.agriinsight.backend.farm;

import com.agriinsight.backend.farm.application.FieldRecord;
import com.agriinsight.backend.farm.domain.Field;
import com.agriinsight.backend.shared.application.CommandExecutionResult;
import com.agriinsight.backend.shared.application.CommandTarget;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

final class FieldHttpTestSupport {

    static final UUID FIELD_ID = UUID.fromString("32000000-0000-0000-0000-000000000001");
    static final UUID EMPLOYEE_ID = UUID.fromString("33000000-0000-0000-0000-000000000001");
    static final UUID COMMAND_ID = UUID.fromString("33000000-0000-0000-0000-000000000006");

    private FieldHttpTestSupport() {
    }

    static FieldRecord field(long version) {
        return field(version, true);
    }

    static FieldRecord field(long version, boolean active) {
        return new FieldRecord(
                FIELD_ID, FarmHttpTestSupport.TENANT_ID, FarmHttpTestSupport.FARM_ID,
                "FIELD-A", "North Field", new BigDecimal("12.5"), Optional.of(EMPLOYEE_ID),
                Optional.of(new Field.Coordinates(
                        new BigDecimal("10.1234"), new BigDecimal("106.7654"))),
                Optional.of("Loam"), Optional.of("Drip"), active, version);
    }

    static CommandExecutionResult.Completed<FieldRecord> completed(
            int status,
            FieldRecord field) {
        return new CommandExecutionResult.Completed<>(
                COMMAND_ID, false, status,
                new CommandTarget("FIELD", field.id(), field.version()), Optional.of(field));
    }
}
