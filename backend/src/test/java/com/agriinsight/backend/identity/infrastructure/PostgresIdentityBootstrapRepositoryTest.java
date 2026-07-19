package com.agriinsight.backend.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agriinsight.backend.identity.application.IdentityBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PostgresIdentityBootstrapRepositoryTest {

    private static final String ISSUER = "https://identity.example.test/issuer";
    private static final String SUBJECT = " Provider-Subject-001 ";

    @Test
    void passesExactIssuerAndSubjectAsBoundParameters() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentityBootstrap expected = new IdentityBootstrap(UUID.randomUUID(), UUID.randomUUID(), true, true);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityBootstrap>>any(),
                eq(ISSUER),
                eq(SUBJECT)))
                .thenReturn(List.of(expected));
        PostgresIdentityBootstrapRepository repository = new PostgresIdentityBootstrapRepository(jdbcTemplate);

        assertThat(repository.findByIssuerAndSubject(ISSUER, SUBJECT)).contains(expected);

        verify(jdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityBootstrap>>any(),
                eq(ISSUER),
                eq(SUBJECT));
    }

    @Test
    void rejectsAnImpossibleDuplicateResolverResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IdentityBootstrap duplicate = new IdentityBootstrap(UUID.randomUUID(), UUID.randomUUID(), true, true);
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<IdentityBootstrap>>any(),
                eq(ISSUER),
                eq(SUBJECT)))
                .thenReturn(List.of(duplicate, duplicate));
        PostgresIdentityBootstrapRepository repository = new PostgresIdentityBootstrapRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.findByIssuerAndSubject(ISSUER, SUBJECT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Identity bootstrap resolver returned more than one row");
    }
}
