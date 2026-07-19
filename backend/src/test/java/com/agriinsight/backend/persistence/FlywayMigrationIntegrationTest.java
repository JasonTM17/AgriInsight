package com.agriinsight.backend.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayMigrationIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18.0-alpine")
            .withDatabaseName("agriinsight")
            .withUsername("agriinsight_migrator")
            .withPassword("integration-only");

    @Test
    void freshPostgresqlAppliesTenantAnchorAndCanonicalCodeConstraint() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load();

        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (var connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            assertThat(insertTenant(connection, "TENANT-01")).isEqualTo(1);
            assertThatThrownBy(() -> insertTenant(connection, "lowercase-code"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTenant(connection, "\u00a0TENANT-02\u00a0"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertTenant(connection, "TENANT-02", "\t\n\u00a0\u2007\u202f"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private int insertTenant(Connection connection, String code) throws SQLException {
        return insertTenant(connection, code, "Integration Tenant");
    }

    private int insertTenant(Connection connection, String code, String displayName) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO tenants (id, code, display_name) VALUES (?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, code);
            statement.setString(3, displayName);
            return statement.executeUpdate();
        }
    }
}
