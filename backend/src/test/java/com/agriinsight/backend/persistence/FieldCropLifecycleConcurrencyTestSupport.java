package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.count;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.execute;
import static com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.operatorConnection;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.Role;
import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;

final class FieldCropLifecycleConcurrencyTestSupport {

    static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final TenantPrincipal PRINCIPAL = new TestPrincipal();
    static final ScopeContext FARM_SCOPE = ScopeContext.domain(
            PRINCIPAL, ScopeContext.Type.FARM, Optional.of(FARM_ID));
    static final ScopeContext TENANT_SCOPE = ScopeContext.tenant(PRINCIPAL);

    private FieldCropLifecycleConcurrencyTestSupport() {
    }

    static void createParents(PostgreSQLContainer postgres, Scenario scenario) throws Exception {
        try (Connection operator = operatorConnection(postgres, "agriinsight")) {
            execute(operator, """
                    INSERT INTO crops (id, tenant_id, code, display_name)
                    VALUES ('%s', '%s', '%s-CROP', '%s Crop');

                    INSERT INTO fields (
                        id, tenant_id, farm_id, code, display_name, area_hectares)
                    VALUES ('%s', '%s', '%s', '%s-FIELD', '%s Field', 2.0000);
                    """.formatted(
                    scenario.cropId(), TENANT_ID, scenario.code(), scenario.code(),
                    scenario.fieldId(), TENANT_ID, FARM_ID, scenario.code(), scenario.code()));
        }
    }

    static void insertLiveSeason(Connection connection, Scenario scenario) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO seasons (
                    id, tenant_id, farm_id, field_id, crop_id, code, display_name,
                    planned_start_date, planned_end_date, planted_area_hectares, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, DATE '2027-01-01', DATE '2027-12-31',
                        1.0000, 'PLANNED')
                """)) {
            statement.setObject(1, scenario.seasonId());
            statement.setObject(2, TENANT_ID);
            statement.setObject(3, FARM_ID);
            statement.setObject(4, scenario.fieldId());
            statement.setObject(5, scenario.cropId());
            statement.setString(6, scenario.code() + "-SEASON");
            statement.setString(7, scenario.code() + " Season");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    static void assertState(
            PostgreSQLContainer postgres,
            Scenario scenario,
            boolean fieldActive,
            boolean cropActive,
            int seasons) throws Exception {
        try (Connection operator = operatorConnection(postgres, "agriinsight")) {
            assertThat(count(operator, """
                    SELECT count(*) FROM fields
                    WHERE id = '%s' AND active = %s
                    """.formatted(scenario.fieldId(), fieldActive))).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM crops
                    WHERE id = '%s' AND active = %s
                    """.formatted(scenario.cropId(), cropActive))).isEqualTo(1);
            assertThat(count(operator, """
                    SELECT count(*) FROM seasons WHERE id = '%s'
                    """.formatted(scenario.seasonId()))).isEqualTo(seasons);
        }
    }

    static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority(Role.TENANT_ADMIN.authority()))));
    }

    record Scenario(UUID fieldId, UUID cropId, UUID seasonId, String code) {

        static Scenario numbered(int base, String code) {
            return new Scenario(identifier(base), identifier(base + 1), identifier(base + 2), code);
        }

        private static UUID identifier(int value) {
            return UUID.fromString("49000000-0000-0000-0000-%012d".formatted(value));
        }
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
