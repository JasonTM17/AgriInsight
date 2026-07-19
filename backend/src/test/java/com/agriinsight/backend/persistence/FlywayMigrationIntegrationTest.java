package com.agriinsight.backend.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayMigrationIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = "provider-subject-001";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.0-alpine")
            .withDatabaseName("agriinsight")
            .withUsername("agriinsight_migrator")
            .withPassword("integration-only");

    @Test
    void freshPostgresqlAppliesFoundationAndLeastPrivilegeIdentityContracts() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (var connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            assertThat(insertTenant(connection, TENANT_ID, "TENANT-01")).isEqualTo(1);
            assertThatThrownBy(() -> insertTenant(connection, "lowercase-code"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTenant(connection, "\u00a0TENANT-02\u00a0"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTenant(connection, "TENANT-02", "\t\n\u00a0\u2007\u202f"))
                    .isInstanceOf(SQLException.class);

            insertIdentityFixture(connection);
            assertIdentityCatalog(connection);
            assertBootstrapResolver(connection, ISSUER, SUBJECT, true);
            assertBootstrapResolver(connection, ISSUER, SUBJECT.toUpperCase(), false);
            assertBootstrapResolver(connection, ISSUER, "' OR '1'='1", false);
            assertThatThrownBy(() -> insertExternalIdentity(connection, TENANT_ID, PROFILE_ID, ISSUER, SUBJECT))
                    .isInstanceOf(SQLException.class);
            assertLeastPrivilegeBootstrapAccess(connection);
        }
    }

    private int insertTenant(Connection connection, String code) throws SQLException {
        return insertTenant(connection, code, "Integration Tenant");
    }

    private int insertTenant(Connection connection, String code, String displayName) throws SQLException {
        return insertTenant(connection, UUID.randomUUID(), code, displayName);
    }

    private int insertTenant(Connection connection, UUID id, String code) throws SQLException {
        return insertTenant(connection, id, code, "Integration Tenant");
    }

    private int insertTenant(Connection connection, UUID id, String code, String displayName) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO tenants (id, code, display_name) VALUES (?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, code);
            statement.setString(3, displayName);
            return statement.executeUpdate();
        }
    }

    private void insertIdentityFixture(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO user_profiles (id, tenant_id, display_name, email)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, PROFILE_ID);
            statement.setObject(2, TENANT_ID);
            statement.setString(3, "Integration User");
            statement.setString(4, "integration.user@example.test");
            statement.executeUpdate();
        }
        insertExternalIdentity(connection, TENANT_ID, PROFILE_ID, ISSUER, SUBJECT);
    }

    private void insertExternalIdentity(
            Connection connection,
            UUID tenantId,
            UUID profileId,
            String issuer,
            String subject) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO external_identities (id, tenant_id, user_profile_id, issuer, subject)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenantId);
            statement.setObject(3, profileId);
            statement.setString(4, issuer);
            statement.setString(5, subject);
            statement.executeUpdate();
        }
    }

    private void assertIdentityCatalog(Connection connection) throws SQLException {
        assertThat(queryCount(connection, "SELECT count(*) FROM permissions")).isEqualTo(19);
        assertThat(queryCount(connection, "SELECT count(*) FROM roles")).isEqualTo(7);
        assertThat(queryCount(connection, "SELECT count(*) FROM role_permissions WHERE role_code = 'TENANT_ADMIN'"))
                .isEqualTo(19);
        assertThat(queryCount(connection, "SELECT count(*) FROM role_permissions WHERE role_code = 'SUPPLIER'"))
                .isZero();
        assertThat(queryCount(connection, """
                SELECT count(*)
                FROM role_permissions
                WHERE role_code = 'SUPPLIER'
                  AND permission_code IN ('COST_READ', 'COST_MANAGE')
                """))
                .isZero();
    }

    private long queryCount(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private void assertBootstrapResolver(
            Connection connection,
            String issuer,
            String subject,
            boolean expectedMatch) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT profile_id, tenant_id, profile_active, tenant_active
                FROM agriinsight_security.resolve_identity_bootstrap(?, ?)
                """)) {
            statement.setString(1, issuer);
            statement.setString(2, subject);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isEqualTo(expectedMatch);
                if (expectedMatch) {
                    assertThat(result.getObject("profile_id", UUID.class)).isEqualTo(PROFILE_ID);
                    assertThat(result.getObject("tenant_id", UUID.class)).isEqualTo(TENANT_ID);
                    assertThat(result.getBoolean("profile_active")).isTrue();
                    assertThat(result.getBoolean("tenant_active")).isTrue();
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }

    private void assertLeastPrivilegeBootstrapAccess(Connection ownerConnection) throws Exception {
        String role = "identity_bootstrap_test";
        String password = UUID.randomUUID().toString();
        try (var statement = ownerConnection.createStatement()) {
            statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
        }

        Properties credentials = new Properties();
        credentials.setProperty("user", role);
        credentials.setProperty("password", password);
        try (var limitedConnection = DriverManager.getConnection(POSTGRESQL.getJdbcUrl(), credentials)) {
            assertThatThrownBy(() -> assertBootstrapResolver(limitedConnection, ISSUER, SUBJECT, true))
                    .isInstanceOf(SQLException.class);

            try (var statement = ownerConnection.createStatement()) {
                statement.execute("GRANT USAGE ON SCHEMA agriinsight_security TO " + role);
                statement.execute("GRANT EXECUTE ON FUNCTION "
                        + "agriinsight_security.resolve_identity_bootstrap(TEXT, TEXT) TO " + role);
            }

            assertBootstrapResolver(limitedConnection, ISSUER, SUBJECT, true);
            assertThatThrownBy(() -> queryCount(limitedConnection, "SELECT count(*) FROM external_identities"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
