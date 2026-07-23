package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migratorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.runtimeConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class RuntimeGrantConvergenceIntegrationTest {

    private static final String TENANT_ID = "71000000-0000-0000-0000-000000000001";
    private static final String PROFILE_ID = "72000000-0000-0000-0000-000000000001";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void migrateDatabase() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
    }

    @Test
    void repeatableGrantsConvergeFromLegacyTableWideUpdate() throws Exception {
        try (var migrator = migratorConnection(POSTGRESQL, "agriinsight")) {
            execute(migrator, """
                    GRANT UPDATE ON seasons, user_farm_assignments, activities,
                        activity_assignees, inventory_transactions
                    TO agriinsight_runtime
                    """);
            execute(
                    migrator,
                    com.agriinsight.backend.persistence.support.SqlTestResources.projectFile(
                            "backend/src/main/resources/db/migration/R__tenant_rls_helpers_and_grants.sql"));
        }

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('seasons'), ('user_farm_assignments'),
                            ('activities'), ('activity_assignees'),
                            ('inventory_transactions')) AS scoped(table_name)
                     WHERE NOT has_table_privilege(
                            'agriinsight_runtime', 'public.' || table_name, 'UPDATE')
                    """)).isEqualTo(5);
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('seasons', 'status'),
                            ('user_farm_assignments', 'revoked_at'),
                            ('activities', 'title'),
                            ('activity_assignees', 'revoked_at'),
                            ('inventory_transactions', 'version'))
                           AS allowed(table_name, column_name)
                     WHERE has_column_privilege(
                            'agriinsight_runtime', 'public.' || table_name, column_name, 'UPDATE')
                    """)).isEqualTo(5);
        }
    }

    @Test
    void sensitiveReadTablesRemainDeniedBehindBoundedHelpers() throws Exception {
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('public.external_identities'),
                            ('public.tenant_audit_events')) AS sensitive(table_name)
                     WHERE has_table_privilege(
                            'agriinsight_runtime', table_name, 'SELECT')
                    """)).isZero();
            assertThat(count(operator, """
                    SELECT count(*)
                      FROM (VALUES
                            ('agriinsight_security.list_external_identities(uuid,uuid,boolean,integer,integer)'),
                            ('agriinsight_security.list_tenant_audit_events(uuid,uuid,text,text,uuid,text,integer,integer)'))
                           AS helper(signature)
                     WHERE has_function_privilege(
                            'agriinsight_runtime', signature, 'EXECUTE')
                    """)).isEqualTo(2);
        }
    }

    @Test
    void boundedHelpersEnforceTenantContextAndReturnOnlySafeColumns() throws Exception {
        try (var migrator = migratorConnection(POSTGRESQL, "agriinsight")) {
            migrator.setAutoCommit(false);
            execute(migrator, "SELECT set_config('app.tenant_id', '" + TENANT_ID + "', true)");
            execute(migrator, """
                    INSERT INTO tenants (id, code, display_name)
                    VALUES ('%s', 'READ-HELPER', 'Read helper tenant');
                    INSERT INTO user_profiles (id, tenant_id, display_name)
                    VALUES ('%s', '%s', 'Read helper user');
                    INSERT INTO external_identities (
                        id, tenant_id, user_profile_id, issuer, subject)
                    VALUES (
                        '73000000-0000-0000-0000-000000000001',
                        '%s', '%s', 'https://issuer.example.test', 'never-return-this-subject');
                    INSERT INTO tenant_audit_events (
                        id, tenant_id, actor_profile_id, actor_type, actor_reference,
                        action, target_type, target_id, target_reference,
                        reason_code, correlation_id, outcome)
                    VALUES (
                        '74000000-0000-0000-0000-000000000001',
                        '%s', '%s', 'TENANT_USER', 'never-return-this-actor-reference',
                        'FIRST_TENANT_ADMIN_PROVISIONED', 'USER_PROFILE', '%s',
                        'never-return-this-target-reference', 'unsafe reason',
                        'unsafe correlation!', 'SUCCEEDED')
                    """.formatted(
                    TENANT_ID,
                    PROFILE_ID, TENANT_ID,
                    TENANT_ID, PROFILE_ID,
                    TENANT_ID, PROFILE_ID, PROFILE_ID));
            migrator.commit();
        }

        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            execute(runtime, "SELECT set_config('app.tenant_id', '" + TENANT_ID + "', true)");
            assertThat(count(runtime, """
                    SELECT count(*)
                      FROM agriinsight_security.list_external_identities(
                           '%s', '%s', TRUE, 101, 0)
                     WHERE identity_issuer = 'https://issuer.example.test'
                       AND identity_active
                    """.formatted(TENANT_ID, PROFILE_ID))).isEqualTo(1);
            assertThat(count(runtime, """
                    SELECT count(*)
                      FROM agriinsight_security.list_tenant_audit_events(
                           '%s', '%s', 'FIRST_TENANT_ADMIN_PROVISIONED',
                           'USER_PROFILE', '%s', 'SUCCEEDED', 101, 0)
                     WHERE event_reason_code IS NULL
                       AND event_correlation_id IS NULL
                    """.formatted(TENANT_ID, PROFILE_ID, PROFILE_ID))).isEqualTo(1);
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*)
                      FROM agriinsight_security.list_external_identities(
                           '%s', '%s', NULL, 102, 0)
                    """.formatted(TENANT_ID, PROFILE_ID)))
                    .isInstanceOf(java.sql.SQLException.class);
        }

        try (var runtime = runtimeConnection(POSTGRESQL, "agriinsight")) {
            runtime.setAutoCommit(false);
            execute(runtime, "SELECT set_config('app.tenant_id', '" + TENANT_ID + "', true)");
            assertThatThrownBy(() -> count(runtime, """
                    SELECT count(*)
                      FROM agriinsight_security.list_tenant_audit_events(
                           '71000000-0000-0000-0000-000000000099',
                           NULL, NULL, NULL, NULL, NULL, 1, 0)
                    """))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
