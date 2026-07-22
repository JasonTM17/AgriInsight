package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.HarvestQuery;
import com.agriinsight.backend.operations.domain.Harvest;
import com.agriinsight.backend.operations.domain.HarvestCorrectionKind;
import com.agriinsight.backend.operations.infrastructure.PostgresHarvestStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresHarvestStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID CROP_ID = UUID.fromString("41000000-0000-0000-0000-000000000002");
    private static final UUID FIELD_ID = UUID.fromString("41000000-0000-0000-0000-000000000003");
    private static final UUID SEASON_ID = UUID.fromString("41000000-0000-0000-0000-000000000006");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("41000000-0000-0000-0000-000000000008");
    private static final UUID HARVEST_ID = UUID.fromString("64000000-0000-0000-0000-000000000001");
    private static final UUID CORRECTION_ID = UUID.fromString("64000000-0000-0000-0000-000000000002");

    @Container
    private static final PostgreSQLContainer POSTGRESQL =
            com.agriinsight.backend.persistence.support.PostgresIntegrationSupport.container();

    @BeforeAll
    static void prepareDatabase() throws Exception {
        migrateAndSeed(POSTGRESQL);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedManagerReadsPostsCorrectsAndLosesAccessAfterRevocation() throws Throwable {
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal);
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresHarvestStore store = new PostgresHarvestStore(harness.jdbcTemplate());
            ScopeContext scope = ScopeContext.domain(
                    principal, ScopeContext.Type.FARM, Optional.of(FARM_ID));

            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                        VALUES ('64000000-0000-0000-0000-000000000010', ?, ?, 'FARM_MANAGER')
                        """, TENANT_ID, PROFILE_ID);
                assertThat(store.findAll(scope, query()).items()).hasSize(1);
                assertThat(store.postTargetAvailable(
                        scope, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID,
                        LocalDate.parse("2027-09-03"))).isTrue();

                var posted = store.append(scope, original()).orElseThrow();
                assertThat(posted.quantityKg()).isEqualByComparingTo("1250");
                var corrected = store.append(scope, correction()).orElseThrow();
                assertThat(corrected.correctsHarvestId()).contains(HARVEST_ID);
                assertThat(store.append(scope, correctionWithId(UUID.randomUUID()))).isEmpty();

                harness.jdbcTemplate().update("""
                        UPDATE user_farm_assignments
                           SET revoked_at = CURRENT_TIMESTAMP, version = version + 1,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE tenant_id = ? AND id = ? AND version = 0
                        """, TENANT_ID, ASSIGNMENT_ID);
                assertThat(store.findAll(scope, query()).items()).isEmpty();
                assertThat(store.append(scope, new Harvest(
                        UUID.randomUUID(), TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID,
                        PROFILE_ID, LocalDate.parse("2027-09-04"), new BigDecimal("10"),
                        BigDecimal.ZERO, Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()))).isEmpty();
                return null;
            });
        }
    }

    private Harvest original() {
        return new Harvest(
                HARVEST_ID, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, PROFILE_ID,
                LocalDate.parse("2027-09-03"), new BigDecimal("1250"), new BigDecimal("25"),
                Optional.of("A"), Optional.of(new BigDecimal("30000000")),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private Harvest correction() {
        return correctionWithId(CORRECTION_ID);
    }

    private Harvest correctionWithId(UUID id) {
        return new Harvest(
                id, TENANT_ID, FARM_ID, FIELD_ID, SEASON_ID, CROP_ID, PROFILE_ID,
                LocalDate.parse("2027-09-03"), new BigDecimal("1240"), new BigDecimal("20"),
                Optional.of("A"), Optional.of(new BigDecimal("29800000")),
                Optional.of(HARVEST_ID), Optional.of(HarvestCorrectionKind.REPLACE),
                Optional.of("Scale reconciliation"));
    }

    private HarvestQuery query() {
        return new HarvestQuery(
                25, 0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_FARM_MANAGER"))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
