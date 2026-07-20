package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.bootstrapRoles;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.migrate;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Permission;
import com.agriinsight.backend.authorization.domain.Role;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class AuthorizationCatalogIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void migrateDatabase() throws Exception {
        bootstrapRoles(POSTGRESQL, "agriinsight");
        migrate(POSTGRESQL, "agriinsight");
    }

    @Test
    void fixedJavaRoleGrantsMatchTheMigratedDatabaseCatalog() throws Exception {
        Map<Role, Set<Permission>> databaseGrants = loadDatabaseGrants();

        assertThat(databaseGrants).containsOnlyKeys(Role.values());
        for (Role role : Role.values()) {
            assertThat(databaseGrants.get(role))
                    .as("permissions for role %s", role)
                    .containsExactlyInAnyOrderElementsOf(role.permissions());
        }
    }

    private Map<Role, Set<Permission>> loadDatabaseGrants() throws SQLException {
        Map<Role, Set<Permission>> grants = new EnumMap<>(Role.class);
        try (var connection = operatorConnection(POSTGRESQL, "agriinsight");
                var statement = connection.prepareStatement("""
                        SELECT role_row.code, grant_row.permission_code
                          FROM roles role_row
                          LEFT JOIN role_permissions grant_row
                            ON grant_row.role_code = role_row.code
                         ORDER BY role_row.code, grant_row.permission_code
                        """);
                var results = statement.executeQuery()) {
            while (results.next()) {
                Role role = Role.valueOf(results.getString("code"));
                Set<Permission> permissions = grants.computeIfAbsent(
                        role, ignored -> EnumSet.noneOf(Permission.class));
                String permission = results.getString("permission_code");
                if (permission != null) {
                    permissions.add(Permission.valueOf(permission));
                }
            }
        }
        return grants;
    }
}
