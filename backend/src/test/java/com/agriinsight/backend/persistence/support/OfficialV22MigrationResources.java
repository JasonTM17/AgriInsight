package com.agriinsight.backend.persistence.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Reconstructs the official V22 migration set from release commit
 * 6927eeda70981c2461e85a165834e2464ba793d1 and rejects drift before executing it.
 */
final class OfficialV22MigrationResources {

    private static final String REPEATABLE_MIGRATION = "R__tenant_rls_helpers_and_grants.sql";
    private static final String REPEATABLE_SHA256 =
            "d972a95532bfb2ba727536afe00a568607e589b186e74d448f131bb5bb3cce21";
    private static final List<MigrationFingerprint> VERSIONED_MIGRATIONS = List.of(
            migration("V1__create_tenant_anchor.sql",
                    "f5e78b1ad272834605b60047162a692318c46def564506ee08276abd7cc4e499"),
            migration("V2__create_identity_tables.sql",
                    "5e07ec1ef32b2a3028d390a091d14671ba2f1c4e53503449e21853c0f1a378c2"),
            migration("V3__seed_permissions_and_roles.sql",
                    "205a9dcf075b996424e5a31697482aefba3f96154b26f34d0568b7f0b02b04cc"),
            migration("V4__add_tenant_security_and_idempotency.sql",
                    "05acad125a7171c7a9be867d496a802c200d17b26cbab870f7f39eaf44c68d02"),
            migration("V5__create_farm_and_operations_tables.sql",
                    "2ac35226e556c2cf31b54a605c9a8dd1985489ed71beb1c4bfa474ba7626f94e"),
            migration("V6__add_farm_and_operations_rls_policies.sql",
                    "0c0e2e100de1ee40882a978b0f11cf8b05dda2428685a446b302dbf087a29658"),
            migration("V7__serialize_farm_lifecycle_dependencies.sql",
                    "7304ad5b86a5ac2cab8f9d38c04f627fc22828a096cc90928f10c3e0469015cf"),
            migration("V8__serialize_field_crop_and_season_lifecycle.sql",
                    "a62736039f9329e5a2a14e845a4172f314b78a4e30be3a495f86e7bc433d34cd"),
            migration("V9__serialize_employee_lifecycle_dependencies.sql",
                    "9c91534f34e31558c1807392e241ae11d92e9af4ecbed5bee2238ba9ccac8381"),
            migration("V10__serialize_farm_assignment_profile_lifecycle.sql",
                    "a4e294fa14234d32dd235cfd1abaadd64148ff44dbbd886008fe9eb6d7433e18"),
            migration("V11__serialize_activity_season_lifecycle.sql",
                    "9e0be7d5a96db97debf7b43b9722020c065597e0bd3d322998d6336d0f09f556"),
            migration("V12__create_inventory_tables.sql",
                    "4b6ae4e2f2e51ac9efd428b8b4b8923f847ec801ebedabe3a193cda974d758a4"),
            migration("V13__add_inventory_rls_policies.sql",
                    "1c38362454ca897493d04565b435a1a8a320bc65d1439a527c160f50497b012d"),
            migration("V14__serialize_warehouse_assignment_lifecycle.sql",
                    "a8fdcda0de71c3783e22f7b5ae67216217e907483e7098b98675eb7af6ec37dc"),
            migration("V15__harden_inventory_scope_and_indexes.sql",
                    "25056c25d6573e9a77bfefa10ab1ceefdc4f90b383c2a324681f3e90de1e0f7f"),
            migration("V16__create_operating_cost_ledger.sql",
                    "a4becaabb4859a4ad62ba96f67c78e450753f9f1d9a5627fd03450f55d9f1344"),
            migration("V17__add_cost_rls_policies.sql",
                    "9ad9ae6412fb079d02d626d04566bef6db08f80b124a53e48fc1cd38f87339bf"),
            migration("V18__create_outbox_tables.sql",
                    "5c8ac3bd7578fbfd06674997f43be17bc2538860a7b31e9046cb828b2208fba9"),
            migration("V19__add_outbox_rls_and_indexes.sql",
                    "03739631b7178fe7fcf2b542f48cf7eb417ee2a52ecb3a1b0cd75fb5c3cce04d"),
            migration("V20__create_realtime_read_models.sql",
                    "b4de10887a340b999705e3983fe10592e6359369589fdf9bd7c4bfd28711343f"),
            migration("V21__add_realtime_metric_summary_index.sql",
                    "15f4a634db1b55aabe53c78c97ac04df3c85cb71962d9fc6f9e41308db3318bf"),
            migration("V22__create_realtime_operational_alerts.sql",
                    "6912e47c821f63b99b0f852d62a3f4944964f4e853d157c97e6d7056a75adf31"));

    private OfficialV22MigrationResources() {
    }

    static Path copyVerifiedRelease(Path projectRoot) throws IOException {
        Path temporaryArtifacts = projectRoot.resolve("artifacts/_tmp");
        Files.createDirectories(temporaryArtifacts);
        Path target = Files.createTempDirectory(temporaryArtifacts, "official-v22-migrations-");
        try {
            Path migrationSource = projectRoot.resolve("backend/src/main/resources/db/migration");
            for (MigrationFingerprint migration : VERSIONED_MIGRATIONS) {
                Path targetMigration = target.resolve(migration.filename());
                Files.copy(migrationSource.resolve(migration.filename()), targetMigration);
                assertFingerprint(targetMigration, migration.sha256());
            }
            Path repeatableSource = historicalRepeatableMigration(projectRoot);
            Path targetRepeatable = target.resolve(REPEATABLE_MIGRATION);
            Files.copy(repeatableSource, targetRepeatable);
            assertFingerprint(targetRepeatable, REPEATABLE_SHA256);
            return target;
        } catch (IOException | RuntimeException exception) {
            try {
                deleteFlatDirectory(target);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    static void assertReleasedSourcesUnchanged(Path projectRoot) throws IOException {
        Path migrationSource = projectRoot.resolve("backend/src/main/resources/db/migration");
        for (MigrationFingerprint migration : VERSIONED_MIGRATIONS) {
            assertFingerprint(migrationSource.resolve(migration.filename()), migration.sha256());
        }
        assertFingerprint(historicalRepeatableMigration(projectRoot), REPEATABLE_SHA256);
    }

    static String normalizedSha256(Path file) throws IOException {
        String normalized = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
        }
    }

    private static void assertFingerprint(Path file, String expectedSha256) throws IOException {
        String actualSha256 = normalizedSha256(file);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IOException(
                    "Released migration content changed: " + file + " expected=" + expectedSha256
                            + " actual=" + actualSha256);
        }
    }

    private static Path historicalRepeatableMigration(Path projectRoot) {
        return projectRoot.resolve(
                "backend/src/test/resources/official-v22-migrations/" + REPEATABLE_MIGRATION);
    }

    private static void deleteFlatDirectory(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(directory);
    }

    private static MigrationFingerprint migration(String filename, String sha256) {
        return new MigrationFingerprint(filename, sha256);
    }

    private record MigrationFingerprint(String filename, String sha256) {
    }
}
