package com.agriinsight.backend.operations.application;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import com.agriinsight.backend.operations.domain.Employee;
import java.util.Objects;
import java.util.Optional;

public final class EmployeeCommands {

    private EmployeeCommands() {
    }

    public record Create(
            String code,
            String displayName,
            Optional<String> jobTitle,
            TenantAuditMetadata audit) {

        public Create {
            code = Employee.canonicalCode(code);
            displayName = Employee.canonicalDisplayName(displayName);
            jobTitle = Employee.optionalJobTitle(jobTitle);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Update(
            Optional<String> code,
            Optional<String> displayName,
            Optional<Optional<String>> jobTitle,
            long expectedVersion,
            TenantAuditMetadata audit) {

        public Update {
            code = Objects.requireNonNull(code, "code is required")
                    .map(Employee::canonicalCode);
            displayName = Objects.requireNonNull(displayName, "displayName is required")
                    .map(Employee::canonicalDisplayName);
            jobTitle = Objects.requireNonNull(jobTitle, "jobTitle is required")
                    .map(Employee::optionalJobTitle);
            if (code.isEmpty() && displayName.isEmpty() && jobTitle.isEmpty()) {
                throw new IllegalArgumentException("at least one employee value must be provided");
            }
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record Lifecycle(long expectedVersion, TenantAuditMetadata audit) {

        public Lifecycle {
            requireVersion(expectedVersion);
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    private static void requireVersion(long expectedVersion) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }
}
