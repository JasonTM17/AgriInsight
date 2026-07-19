package com.agriinsight.backend.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSchemaReadinessIndicatorTest {

    @Test
    void reportsUpOnlyWhenTheExpectedMigrationIsInstalled() {
        var health = new DatabaseSchemaReadinessIndicator(returningVersion("1"), "1").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("schemaVersion", "1");
    }

    @Test
    void reportsOutOfServiceWhenTheSchemaIsBehind() {
        var health = new DatabaseSchemaReadinessIndicator(returningVersion("0"), "1").health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(health.getDetails()).containsEntry("expectedVersion", "1");
    }

    @Test
    void reportsDownWithoutReturningDatabaseDetailsWhenTheQueryFails() {
        var health = new DatabaseSchemaReadinessIndicator(failingQuery(), "1").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "schema-unavailable");
        assertThat(health.getDetails()).doesNotContainValue("database password leaked in exception");
    }

    private JdbcTemplate returningVersion(String version) {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                return requiredType.cast(version);
            }
        };
    }

    private JdbcTemplate failingQuery() {
        return new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                throw new DataAccessResourceFailureException("database password leaked in exception");
            }
        };
    }
}
