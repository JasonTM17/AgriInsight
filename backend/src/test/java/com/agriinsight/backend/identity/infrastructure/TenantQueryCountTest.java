package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.authorization.domain.ScopeContext;
import com.agriinsight.backend.identity.application.TenantPrincipalPort;
import com.agriinsight.backend.identity.application.TenantUserPage;
import com.agriinsight.backend.identity.application.TenantUserProfile;
import com.agriinsight.backend.identity.application.TenantUserQuery;
import com.agriinsight.backend.shared.security.TenantPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TenantQueryCountTest {

    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void boundedUserListUsesOneParameterizedQueryWithLookaheadLimit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        TenantUserProfile profile = new TenantUserProfile(
                PROFILE_ID,
                TENANT_ID,
                "User A",
                Optional.of("user-a@example.test"),
                true,
                0);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<TenantUserProfile>>any(),
                any(Object[].class)))
                .thenReturn(List.of(profile));

        TenantUserPage page = new PostgresTenantUserStore(jdbcTemplate).findAll(
                ScopeContext.tenant(new TestPrincipal(PROFILE_ID, TENANT_ID)),
                new TenantUserQuery(25, 50, Optional.of(true), Optional.of("user")));

        assertThat(page.items()).containsExactly(profile);
        assertThat(page.hasMore()).isFalse();
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1)).query(
                sql.capture(),
                org.mockito.ArgumentMatchers.<RowMapper<TenantUserProfile>>any(),
                any(Object[].class));
        assertThat(sql.getValue())
                .contains("ORDER BY lower(display_name), id LIMIT ? OFFSET ?");
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void principalPermissionEnrichmentUsesOneJoinedQuery() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                eq(PROFILE_ID),
                eq(TENANT_ID)))
                .thenReturn(List.of());

        TenantPrincipalPort repository = new PostgresTenantPrincipalRepository(jdbcTemplate);

        assertThat(repository.findActiveByProfileAndTenant(PROFILE_ID, TENANT_ID)).isEmpty();
        verify(jdbcTemplate, times(1)).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                eq(PROFILE_ID),
                eq(TENANT_ID));
        verifyNoMoreInteractions(jdbcTemplate);
    }

    private record TestPrincipal(UUID profileId, UUID tenantId) implements TenantPrincipal {

        @Override
        public String getName() {
            return profileId.toString();
        }
    }
}
