package com.agriinsight.backend.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.operations.api.EmployeeUpdateRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EmployeeRequestContractsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omittedClearFlagDefaultsToFalseAndExplicitClearIsSemantic() throws Exception {
        EmployeeUpdateRequest omitted = mapper.readValue(
                "{\"displayName\":\"Updated\"}", EmployeeUpdateRequest.class);
        EmployeeUpdateRequest cleared = mapper.readValue(
                "{\"clearJobTitle\":true}", EmployeeUpdateRequest.class);

        assertThat(omitted.clearJobTitle()).isFalse();
        assertThat(cleared.clearJobTitle()).isTrue();
    }

    @Test
    void rejectsSettingAndClearingJobTitleTogether() {
        assertThatThrownBy(() -> new EmployeeUpdateRequest(
                null, "Updated", "Technician", true, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("set and cleared");
    }
}
