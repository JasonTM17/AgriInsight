package com.agriinsight.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "AgriInsight Backend")
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AgriInsightBackendApplication {

    private static final String EXIT_ON_COMPLETE_PROPERTY =
            "agriinsight.migration.exit-on-complete";
    private static final String WEB_APPLICATION_TYPE_PROPERTY =
            "spring.main.web-application-type";
    private static final String FLYWAY_ENABLED_PROPERTY =
            "spring.flyway.enabled";

    public static void main(String[] args) {
        var context = SpringApplication.run(AgriInsightBackendApplication.class, args);
        var environment = context.getEnvironment();
        if (isMigrationExitRequested(environment)) {
            try {
                requireFlywayForMigrationExit(environment);
            } catch (RuntimeException error) {
                context.close();
                throw error;
            }
            System.exit(SpringApplication.exit(context));
        }
    }

    static boolean isMigrationExitRequested(Environment environment) {
        return environment.getProperty(EXIT_ON_COMPLETE_PROPERTY, Boolean.class, false)
                && "none".equalsIgnoreCase(
                        environment.getProperty(WEB_APPLICATION_TYPE_PROPERTY, ""));
    }

    static void requireFlywayForMigrationExit(Environment environment) {
        if (!environment.getProperty(FLYWAY_ENABLED_PROPERTY, Boolean.class, false)) {
            throw new IllegalStateException(
                    "The migration exit runner requires spring.flyway.enabled=true");
        }
    }
}
