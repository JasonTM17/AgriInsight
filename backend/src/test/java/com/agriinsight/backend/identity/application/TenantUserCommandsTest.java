package com.agriinsight.backend.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agriinsight.backend.authorization.application.TenantAuditMetadata;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantUserCommandsTest {

    private static final TenantAuditMetadata AUDIT =
            new TenantAuditMetadata(
                    Optional.of("ACCESS_APPROVED"),
                    Optional.of("request-01"));

    @Test
    void identityCommandsRedactPersonalAndProviderIdentifiersFromToString() {
        TenantUserCommands.Create create = new TenantUserCommands.Create(
                "Sensitive Name",
                Optional.of("sensitive@example.test"),
                "https://identity.example.test/issuer",
                "sensitive-subject",
                AUDIT);
        TenantUserCommands.LinkIdentity link = new TenantUserCommands.LinkIdentity(
                "https://identity.example.test/issuer",
                "another-sensitive-subject",
                AUDIT);

        assertThat(create.toString())
                .contains("<redacted>")
                .doesNotContain("Sensitive Name", "sensitive@example.test", "sensitive-subject");
        assertThat(link.toString())
                .contains("<redacted>")
                .doesNotContain("another-sensitive-subject");
    }

    @Test
    void queryAndVersionBoundsFailBeforePersistence() {
        assertThatThrownBy(() -> new TenantUserQuery(
                101,
                0,
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new TenantUserCommands.Lifecycle(-1, AUDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }
}
