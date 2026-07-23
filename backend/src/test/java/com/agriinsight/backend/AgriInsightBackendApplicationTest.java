package com.agriinsight.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AgriInsightBackendApplicationTest {

    @Test
    void migrationExitRequiresExplicitNonWebMode() {
        var environment = new MockEnvironment()
                .withProperty("agriinsight.migration.exit-on-complete", "true");

        assertThat(AgriInsightBackendApplication.isMigrationExitRequested(environment))
                .isFalse();
    }

    @Test
    void migrationExitIsEnabledOnlyForTheDedicatedRunner() {
        var environment = new MockEnvironment()
                .withProperty("agriinsight.migration.exit-on-complete", "true")
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("spring.flyway.enabled", "true");

        assertThat(AgriInsightBackendApplication.isMigrationExitRequested(environment))
                .isTrue();

        AgriInsightBackendApplication.requireFlywayForMigrationExit(environment);
    }

    @Test
    void migrationExitFailsFastWhenFlywayIsDisabled() {
        var environment = new MockEnvironment()
                .withProperty("agriinsight.migration.exit-on-complete", "true")
                .withProperty("spring.main.web-application-type", "none");

        assertThat(AgriInsightBackendApplication.isMigrationExitRequested(environment))
                .isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> AgriInsightBackendApplication.requireFlywayForMigrationExit(
                                environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.enabled=true");
    }
}
