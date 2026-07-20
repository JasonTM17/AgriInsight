package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.persistence.support.SqlTestResources;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers
class TenantProvisioningIntegrationTest {

    private static final String SCRIPT_IN_CONTAINER = "/tmp/provision-tenant-admin.sql";

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabaseAndScript() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
        POSTGRESQL.copyFileToContainer(
                MountableFile.forHostPath(SqlTestResources.projectPath(
                        "backend/ops/postgres/provision-tenant-admin.sql")),
                SCRIPT_IN_CONTAINER);
    }

    @Test
    void operatorCanProvisionTwoTenantsWhileDuplicatesFailAtomically() throws Exception {
        assertThat(provision("TENANT-A", "subject-a").getExitCode()).isZero();
        assertThat(provision("TENANT-B", "subject-b").getExitCode()).isZero();
        assertThat(provision("TENANT-A", "subject-a-retry").getExitCode()).isNotZero();
        assertThat(provision("TENANT-C", "subject-a").getExitCode()).isNotZero();

        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM tenants WHERE code IN ('TENANT-A', 'TENANT-B')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*) FROM user_profiles profile
                    JOIN tenants tenant ON tenant.id = profile.tenant_id
                    WHERE tenant.code IN ('TENANT-A', 'TENANT-B')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*) FROM external_identities
                    WHERE subject IN ('subject-a', 'subject-b')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*) FROM user_roles assignment
                    JOIN tenants tenant ON tenant.id = assignment.tenant_id
                    WHERE tenant.code IN ('TENANT-A', 'TENANT-B')
                      AND assignment.role_code = 'TENANT_ADMIN'
                      AND assignment.revoked_at IS NULL
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*) FROM tenant_audit_events
                    WHERE action = 'FIRST_TENANT_ADMIN_PROVISIONED'
                      AND outcome = 'SUCCEEDED'
                      AND target_reference IN ('TENANT-A', 'TENANT-B')
                    """)).isEqualTo(2);
            assertThat(count(operator, """
                    SELECT count(*) FROM activity_types activity_type
                    JOIN tenants tenant ON tenant.id = activity_type.tenant_id
                    WHERE tenant.code IN ('TENANT-A', 'TENANT-B')
                    """)).isEqualTo(16);
            assertThat(count(operator, "SELECT count(*) FROM tenants WHERE code = 'TENANT-C'"))
                    .isZero();
        }
    }

    @Test
    void concurrentDuplicateProvisioningCommitsExactlyOneTenant() throws Exception {
        Callable<ExecResult> duplicate = () -> provision("TENANT-CONCURRENT", "subject-concurrent");
        List<ExecResult> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(duplicate);
            var second = executor.submit(duplicate);
            results = List.of(first.get(), second.get());
        }

        assertThat(results).extracting(ExecResult::getExitCode).containsExactlyInAnyOrder(0, 3);
        try (var operator = operatorConnection(POSTGRESQL, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM tenants WHERE code = 'TENANT-CONCURRENT'
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM external_identities WHERE subject = 'subject-concurrent'
                    """)).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM activity_types activity_type
                    JOIN tenants tenant ON tenant.id = activity_type.tenant_id
                    WHERE tenant.code = 'TENANT-CONCURRENT'
                    """)).isEqualTo(8);
        }
    }

    private static ExecResult provision(String tenantCode, String subject) throws Exception {
        return POSTGRESQL.execInContainer(
                "psql",
                "-X",
                "-v", "ON_ERROR_STOP=1",
                "-v", "tenant_code=" + tenantCode,
                "-v", "tenant_display_name=" + tenantCode + " Display",
                "-v", "admin_display_name=" + tenantCode + " Admin",
                "-v", "admin_email=" + tenantCode.toLowerCase() + "@example.test",
                "-v", "issuer=https://identity.example.test/issuer",
                "-v", "subject=" + subject,
                "-v", "correlation_id=integration-test",
                "-U", "agriinsight_migrator",
                "-d", "agriinsight",
                "-f", SCRIPT_IN_CONTAINER);
    }
}
