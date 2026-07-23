package com.agriinsight.backend.persistence;

import static com.agriinsight.backend.persistence.support.FarmOperationsTestFixtures.migrateAndSeed;
import static org.assertj.core.api.Assertions.assertThat;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.operations.application.ActivityReadPageQuery;
import com.agriinsight.backend.operations.domain.ActivityLog;
import com.agriinsight.backend.operations.domain.ActivityLogCorrectionKind;
import com.agriinsight.backend.operations.domain.ActivityLogUnit;
import com.agriinsight.backend.operations.infrastructure.PostgresActivityLogStore;
import com.agriinsight.backend.operations.infrastructure.PostgresActivityLogReadStore;
import com.agriinsight.backend.persistence.support.TenantTransactionTestHarness;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
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
class PostgresActivityLogStoreIntegrationTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000041");
    private static final UUID PROFILE_ID = UUID.fromString("41000000-0000-0000-0000-000000000005");
    private static final UUID FARM_ID = UUID.fromString("41000000-0000-0000-0000-000000000001");
    private static final UUID ACTIVITY_ID = UUID.fromString("41000000-0000-0000-0000-000000000007");
    private static final UUID OTHER_ACTIVITY_ID =
            UUID.fromString("42000000-0000-0000-0000-000000000007");
    private static final UUID EMPLOYEE_ID = UUID.fromString("41000000-0000-0000-0000-000000000004");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("41000000-0000-0000-0000-000000000009");
    private static final UUID LOG_ID = UUID.fromString("63000000-0000-0000-0000-000000000001");
    private static final UUID CORRECTION_ID = UUID.fromString("63000000-0000-0000-0000-000000000002");
    private static final UUID COMPETING_ID = UUID.fromString("63000000-0000-0000-0000-000000000003");

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
    void assignedWorkerAppendsAndCorrectsOnlyWhileAssignmentIsActive() throws Throwable {
        TenantPrincipal principal = new TestPrincipal();
        authenticate(principal);
        try (TenantTransactionTestHarness harness = TenantTransactionTestHarness.runtime(
                POSTGRESQL, "agriinsight")) {
            PostgresActivityLogStore store = new PostgresActivityLogStore(harness.jdbcTemplate());
            PostgresActivityLogReadStore reads =
                    new PostgresActivityLogReadStore(harness.jdbcTemplate());
            ScopeContext activityScope = ScopeContext.domain(
                    principal, ScopeContext.Type.ACTIVITY, Optional.of(ACTIVITY_ID));

            harness.withinTenant(() -> {
                harness.jdbcTemplate().update("""
                        INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                        VALUES ('63000000-0000-0000-0000-000000000010', ?, ?, 'FIELD_WORKER')
                        """, TENANT_ID, PROFILE_ID);
                var access = store.resolveAccess(activityScope, ACTIVITY_ID).orElseThrow();
                assertThat(access.manager()).isFalse();
                assertThat(access.farmId()).isEqualTo(FARM_ID);
                assertThat(access.workerEmployeeId()).contains(EMPLOYEE_ID);

                var appended = store.append(activityScope, original()).orElseThrow();
                assertThat(appended.quantity()).hasValueSatisfying(quantity ->
                        assertThat(quantity).isEqualByComparingTo("100"));
                assertThat(store.findById(activityScope, ACTIVITY_ID, LOG_ID)).contains(appended);

                var corrected = store.append(activityScope, correction(CORRECTION_ID)).orElseThrow();
                assertThat(corrected.correctsLogId()).contains(LOG_ID);
                assertThat(corrected.correctionKind())
                        .contains(ActivityLogCorrectionKind.REPLACE);
                assertThat(store.append(activityScope, correction(COMPETING_ID))).isEmpty();

                var page = reads.findAll(
                        activityScope, ACTIVITY_ID, access, new ActivityReadPageQuery(1, 0));
                assertThat(page.items()).hasSize(1);
                assertThat(page.hasMore()).isTrue();
                var history = reads.findHistory(
                        activityScope,
                        ACTIVITY_ID,
                        LOG_ID,
                        access,
                        new ActivityReadPageQuery(10, 0));
                assertThat(history.items()).extracting(item -> item.id())
                        .containsExactly(LOG_ID, CORRECTION_ID);

                harness.jdbcTemplate().update("""
                        UPDATE activity_assignees
                           SET revoked_at = CURRENT_TIMESTAMP,
                               version = version + 1,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE tenant_id = ? AND id = ? AND version = 0
                        """, TENANT_ID, ASSIGNMENT_ID);
                assertThat(store.resolveAccess(activityScope, ACTIVITY_ID)).isEmpty();
                assertThat(store.append(activityScope, new ActivityLog(
                        UUID.randomUUID(), TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, PROFILE_ID,
                        Instant.parse("2027-09-01T02:30:00Z"), Optional.of("Late log"),
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()))).isEmpty();

                harness.jdbcTemplate().update("""
                        INSERT INTO user_roles (id, tenant_id, user_profile_id, role_code)
                        VALUES ('63000000-0000-0000-0000-000000000011', ?, ?, 'FARM_MANAGER')
                        """, TENANT_ID, PROFILE_ID);
                var managerAccess = store.resolveAccess(activityScope, ACTIVITY_ID).orElseThrow();
                assertThat(managerAccess.manager()).isTrue();
                assertThat(reads.findAll(
                        activityScope,
                        ACTIVITY_ID,
                        managerAccess,
                        new ActivityReadPageQuery(100, 0)).items())
                        .allMatch(item -> item.tenantId().equals(TENANT_ID));
                ScopeContext otherActivityScope = ScopeContext.domain(
                        principal,
                        ScopeContext.Type.ACTIVITY,
                        Optional.of(OTHER_ACTIVITY_ID));
                assertThat(reads.findAll(
                        otherActivityScope,
                        OTHER_ACTIVITY_ID,
                        managerAccess,
                        new ActivityReadPageQuery(100, 0)).items()).isEmpty();
                return null;
            });
        }
    }

    private ActivityLog original() {
        return new ActivityLog(
                LOG_ID, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, PROFILE_ID,
                Instant.parse("2027-09-01T02:00:00Z"), Optional.of("Harvested batch"),
                Optional.of(new BigDecimal("100")), Optional.of(ActivityLogUnit.KG),
                Optional.of("https://evidence.example/log-1"), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private ActivityLog correction(UUID id) {
        return new ActivityLog(
                id, TENANT_ID, ACTIVITY_ID, EMPLOYEE_ID, PROFILE_ID,
                Instant.parse("2027-09-01T02:05:00Z"), Optional.of("Corrected quantity"),
                Optional.of(new BigDecimal("101")), Optional.of(ActivityLogUnit.KG), Optional.empty(),
                Optional.of(LOG_ID), Optional.of(ActivityLogCorrectionKind.REPLACE),
                Optional.of("Scale reading corrected"));
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_FIELD_WORKER"))));
    }

    private record TestPrincipal() implements TenantPrincipal {
        @Override public UUID profileId() { return PROFILE_ID; }
        @Override public UUID tenantId() { return TENANT_ID; }
        @Override public String getName() { return PROFILE_ID.toString(); }
    }
}
