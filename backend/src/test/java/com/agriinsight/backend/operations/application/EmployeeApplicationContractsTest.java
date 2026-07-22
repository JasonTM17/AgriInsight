package com.agriinsight.backend.operations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmployeeApplicationContractsTest {

    private static final TenantAuditMetadata AUDIT = new TenantAuditMetadata(
            Optional.of("EMPLOYEE_CHANGE"), Optional.of("request-01"));

    @Test
    void updateDistinguishesOmittedJobTitleFromExplicitClear() {
        EmployeeCommands.Update update = new EmployeeCommands.Update(
                Optional.empty(), Optional.empty(), Optional.of(Optional.empty()), 2, AUDIT);

        assertThat(update.jobTitle()).contains(Optional.empty());
    }

    @Test
    void rejectsEmptyUpdatesAndUnboundedQueries() {
        assertThatThrownBy(() -> new EmployeeCommands.Update(
                Optional.empty(), Optional.empty(), Optional.empty(), 0, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> new EmployeeQuery(
                101, 0, Optional.empty(), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }
}
