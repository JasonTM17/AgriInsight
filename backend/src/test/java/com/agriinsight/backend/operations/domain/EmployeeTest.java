package com.agriinsight.backend.operations.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeTest {

    private static final UUID TENANT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID =
            UUID.fromString("36000000-0000-0000-0000-000000000001");

    @Test
    void canonicalizesWorkforceMasterData() {
        Employee employee = new Employee(
                EMPLOYEE_ID, TENANT_ID, " worker-a ", " Nguyen Van A ",
                Optional.of(" Field Technician "));

        assertThat(employee.code()).isEqualTo("WORKER-A");
        assertThat(employee.displayName()).isEqualTo("Nguyen Van A");
        assertThat(employee.jobTitle()).contains("Field Technician");
    }

    @Test
    void rejectsInvalidCodesAndBlankJobTitles() {
        assertThatThrownBy(() -> new Employee(
                EMPLOYEE_ID, TENANT_ID, "worker a", "Nguyen Van A", Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
        assertThatThrownBy(() -> new Employee(
                EMPLOYEE_ID, TENANT_ID, "WORKER-A", "Nguyen Van A", Optional.of("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobTitle");
    }
}
