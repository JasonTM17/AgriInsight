package com.agriinsight.backend.persistence.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresIntegrationSupport {

    public static final String MIGRATOR = "agriinsight_migrator";
    public static final String RUNTIME = "agriinsight_runtime";
    public static final String IDENTITY_DEFINER = "agriinsight_identity_definer";
    public static final String MIGRATOR_PASSWORD = "migrator-integration-only";
    public static final String RUNTIME_PASSWORD = "runtime-integration-only";

    private PostgresIntegrationSupport() {
    }

    public static PostgreSQLContainer container() {
        return new PostgreSQLContainer("postgres:18.0-alpine")
                .withDatabaseName("agriinsight")
                .withUsername("postgres")
                .withPassword("operator-integration-only");
    }

    public static void bootstrapRoles(PostgreSQLContainer container, String database) throws Exception {
        try (Connection connection = operatorConnection(container, database)) {
            execute(connection, SqlTestResources.projectFile("backend/ops/postgres/bootstrap-roles.sql"));
            execute(connection, "ALTER ROLE " + MIGRATOR + " PASSWORD '" + MIGRATOR_PASSWORD + "'");
            execute(connection, "ALTER ROLE " + RUNTIME + " PASSWORD '" + RUNTIME_PASSWORD + "'");
        }
    }

    public static MigrateResult migrate(PostgreSQLContainer container, String database) {
        return flyway(container, database, MIGRATOR, MIGRATOR_PASSWORD, "classpath:db/migration").migrate();
    }

    public static Flyway flyway(
            PostgreSQLContainer container,
            String database,
            String username,
            String password,
            String location) {
        return Flyway.configure()
                .dataSource(jdbcUrl(container, database), username, password)
                .locations(location)
                .validateMigrationNaming(true)
                .load();
    }

    public static Connection operatorConnection(PostgreSQLContainer container, String database)
            throws SQLException {
        return connection(container, database, container.getUsername(), container.getPassword());
    }

    public static Connection migratorConnection(PostgreSQLContainer container, String database)
            throws SQLException {
        return connection(container, database, MIGRATOR, MIGRATOR_PASSWORD);
    }

    public static Connection runtimeConnection(PostgreSQLContainer container, String database)
            throws SQLException {
        return connection(container, database, RUNTIME, RUNTIME_PASSWORD);
    }

    public static Connection connection(
            PostgreSQLContainer container,
            String database,
            String username,
            String password) throws SQLException {
        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
        return DriverManager.getConnection(jdbcUrl(container, database), credentials);
    }

    public static String jdbcUrl(PostgreSQLContainer container, String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                container.getHost(),
                container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                database);
    }

    public static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public static long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("Count query returned no row");
            }
            return result.getLong(1);
        }
    }

    public static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                return null;
            }
            return result.getString(1);
        }
    }
}
