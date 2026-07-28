package com.agriinsight.backend.persistence.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OfficialV22MigrationResourcesTest {

    @Test
    void verifiedReleaseCopyContainsVersionedAndHistoricalRepeatableMigrations() throws Exception {
        Path releaseCopy = SqlTestResources.copyMigrationsThroughV22();
        try {
            try (var files = Files.list(releaseCopy)) {
                assertThat(files.map(path -> path.getFileName().toString()))
                        .hasSize(23)
                        .contains(
                                "V1__create_tenant_anchor.sql",
                                "V22__create_realtime_operational_alerts.sql",
                                "R__tenant_rls_helpers_and_grants.sql");
            }
        } finally {
            SqlTestResources.deleteLegacyMigrations(releaseCopy);
        }
    }
}
