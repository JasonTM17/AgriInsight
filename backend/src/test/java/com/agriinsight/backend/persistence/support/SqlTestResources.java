package com.agriinsight.backend.persistence.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class SqlTestResources {

    private SqlTestResources() {
    }

    public static String projectFile(String relativePath) throws IOException {
        return Files.readString(projectPath(relativePath), StandardCharsets.UTF_8);
    }

    public static Path projectPath(String relativePath) {
        return projectRoot().resolve(relativePath);
    }

    public static String renderPsqlScript(String relativePath, Map<String, String> variables)
            throws IOException {
        String rendered = projectFile(relativePath).lines()
                .filter(line -> !line.stripLeading().startsWith("\\"))
                .reduce("", (left, line) -> left + line + System.lineSeparator());
        for (var variable : variables.entrySet()) {
            rendered = rendered.replace(":'" + variable.getKey() + "'", sqlLiteral(variable.getValue()));
        }
        return rendered;
    }

    public static Path copyLegacyMigrations() throws IOException {
        return copyMigrations("legacy-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql"
        });
    }

    public static Path copyMigrationsThroughV4() throws IOException {
        return copyMigrations("phase-three-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql"
        });
    }

    public static Path copyMigrationsThroughV6() throws IOException {
        return copyMigrations("phase-four-schema-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql"
        });
    }

    public static Path copyMigrationsThroughV7() throws IOException {
        return copyMigrations("phase-four-lifecycle-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql",
                "V7__serialize_farm_lifecycle_dependencies.sql"
        });
    }

    public static Path copyMigrationsThroughV8() throws IOException {
        return copyMigrations("phase-four-master-data-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql",
                "V7__serialize_farm_lifecycle_dependencies.sql",
                "V8__serialize_field_crop_and_season_lifecycle.sql"
        });
    }

    public static Path copyMigrationsThroughV9() throws IOException {
        return copyMigrations("phase-four-workforce-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql",
                "V7__serialize_farm_lifecycle_dependencies.sql",
                "V8__serialize_field_crop_and_season_lifecycle.sql",
                "V9__serialize_employee_lifecycle_dependencies.sql"
        });
    }

    public static Path copyMigrationsThroughV10() throws IOException {
        return copyMigrations("phase-four-assignment-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql",
                "V7__serialize_farm_lifecycle_dependencies.sql",
                "V8__serialize_field_crop_and_season_lifecycle.sql",
                "V9__serialize_employee_lifecycle_dependencies.sql",
                "V10__serialize_farm_assignment_profile_lifecycle.sql"
        });
    }

    public static Path copyMigrationsThroughV22() throws IOException {
        return copyMigrations("official-v22-migrations-", new String[] {
                "V1__create_tenant_anchor.sql",
                "V2__create_identity_tables.sql",
                "V3__seed_permissions_and_roles.sql",
                "V4__add_tenant_security_and_idempotency.sql",
                "V5__create_farm_and_operations_tables.sql",
                "V6__add_farm_and_operations_rls_policies.sql",
                "V7__serialize_farm_lifecycle_dependencies.sql",
                "V8__serialize_field_crop_and_season_lifecycle.sql",
                "V9__serialize_employee_lifecycle_dependencies.sql",
                "V10__serialize_farm_assignment_profile_lifecycle.sql",
                "V11__serialize_activity_season_lifecycle.sql",
                "V12__create_inventory_tables.sql",
                "V13__add_inventory_rls_policies.sql",
                "V14__serialize_warehouse_assignment_lifecycle.sql",
                "V15__harden_inventory_scope_and_indexes.sql",
                "V16__create_operating_cost_ledger.sql",
                "V17__add_cost_rls_policies.sql",
                "V18__create_outbox_tables.sql",
                "V19__add_outbox_rls_and_indexes.sql",
                "V20__create_realtime_read_models.sql",
                "V21__add_realtime_metric_summary_index.sql",
                "V22__create_realtime_operational_alerts.sql"
        });
    }

    private static Path copyMigrations(String prefix, String[] migrationFiles) throws IOException {
        Path root = projectRoot();
        Path target = Files.createTempDirectory(root.resolve("artifacts/_tmp"), prefix);
        Path source = root.resolve("backend/src/main/resources/db/migration");
        for (String file : migrationFiles) {
            Files.copy(source.resolve(file), target.resolve(file));
        }
        return target;
    }

    public static void deleteLegacyMigrations(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.delete(directory);
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static Path projectRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(workingDirectory.resolve("backend"))
                ? workingDirectory
                : workingDirectory.getParent();
    }
}
