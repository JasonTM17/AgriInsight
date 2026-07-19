package com.agriinsight.backend.shared.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("schemaHistory")
@Profile("!test")
public class DatabaseSchemaReadinessIndicator implements HealthIndicator {

    private static final String LATEST_VERSION_QUERY = """
             SELECT version
             FROM flyway_schema_history
             WHERE success = TRUE
               AND version IS NOT NULL
             ORDER BY installed_rank DESC
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String expectedVersion;

    public DatabaseSchemaReadinessIndicator(
            JdbcTemplate jdbcTemplate,
            @Value("${agriinsight.schema.expected-version:1}") String expectedVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.expectedVersion = expectedVersion;
    }

    @Override
    public Health health() {
        try {
            String installedVersion = jdbcTemplate.queryForObject(LATEST_VERSION_QUERY, String.class);
            if (expectedVersion.equals(installedVersion)) {
                return Health.up()
                        .withDetail("schemaVersion", installedVersion)
                        .build();
            }
            return Health.outOfService()
                    .withDetail("schemaVersion", installedVersion == null ? "missing" : installedVersion)
                    .withDetail("expectedVersion", expectedVersion)
                    .build();
        } catch (DataAccessException exception) {
            return Health.down()
                    .withDetail("reason", "schema-unavailable")
                    .build();
        }
    }
}
